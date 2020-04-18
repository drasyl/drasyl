package org.drasyl.core.client.transport;

import akka.actor.ActorRef;
import akka.actor.ActorRefScope;
import akka.actor.ActorSelection$;
import akka.actor.ActorSelectionMessage;
import akka.actor.Address;
import akka.actor.ExtendedActorSystem;
import akka.actor.InternalActorRef;
import akka.actor.LocalRef;
import akka.actor.RepointableRef;
import akka.dispatch.sysmsg.SystemMessage;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import org.drasyl.core.client.P2PActorRef;
import org.drasyl.core.client.P2PActorRefProvider;
import org.drasyl.core.client.transport.event.P2PTransportErrorEvent;
import org.drasyl.core.client.transport.event.P2PTransportLifecycleEvent;
import org.drasyl.core.client.transport.event.P2PTransportListenEvent;
import org.drasyl.core.client.transport.event.P2PTransportShutdownEvent;
import com.typesafe.config.Config;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * This component is responsible for transmitting messages.
 * For this purpose it uses one or more {@link P2PTransportChannel} implementations.
 * Each channel provides its own mechanisms for discovering and discussing other actor systems (e.g. registering and communicating
 * via a central exposed relay server, broadcasting on the local network).
 */
public class P2PTransport {
    private final ExtendedActorSystem system;
    private final EventPublisher eventPublisher;
    private final P2PActorRefProvider provider;
    private final List<P2PTransportChannel> channels;
    private final Address defaultAddress;
    private final Duration startupTimeout;
    private final Duration shutdownTimeout;
    private final Set<Address> addresses;

    public P2PTransport(ExtendedActorSystem system,
                        P2PActorRefProvider provider, EventPublisher eventPublisher,
                        List<P2PTransportChannel> channels,
                        Address defaultAddress,
                        Set<Address> addresses, Duration startupTimeout,
                        Duration shutdownTimeout) {
        this.system = system;
        this.provider = provider;
        this.eventPublisher = eventPublisher;
        this.defaultAddress = defaultAddress;
        this.addresses = addresses;
        this.channels = channels;
        this.startupTimeout = startupTimeout;
        this.shutdownTimeout = shutdownTimeout;
    }

    public P2PTransport(ExtendedActorSystem system, P2PActorRefProvider provider) {
        System.setProperty("io.netty.tryReflectionSetAccessible", "true");
        this.system = system;
        this.provider = provider;
        this.eventPublisher = new EventPublisher(system, log(), 3); // Logging.DebugLevel()

        this.defaultAddress = new Address(P2PActorRefProvider.SCHEME, system.name());
        this.addresses = Set.of(this.defaultAddress);

        Config akkaP2pConfig = this.system.settings().config().getConfig("akka.p2p");
        this.channels = akkaP2pConfig.getStringList("enabled-channels").stream()
                .map(className -> {
                    try {
                        Class<?> clazz = Class.forName(className);
                        Constructor<?> constructor = clazz.getConstructor(
                                String.class, Config.class, P2PTransport.class, ExtendedActorSystem.class
                        );
                        return (P2PTransportChannel) constructor.newInstance(
                                this.system.name(), akkaP2pConfig, this, this.system);
                    } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
                        log().error("Failed to initialize TransportChannel of class {}! Using default RetryRelayP2PTransportChannel instead.\n {}", className, e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (this.channels.isEmpty()) {
            log().warning("No TransportChannels defined! Transport will not be able to forward messages. Please provide appropriate classNames in 'akka.p2p.relay.enabled-channels'.");
        }

        this.startupTimeout = system.settings().config().getDuration("akka.p2p.startup-timeout");
        this.shutdownTimeout = system.settings().config().getDuration("akka.p2p.shutdown-timeout");
    }

    /**
     * Address to be used in RootActorPath of refs generated for this transport.
     */
    public Set<Address> addresses() {
        return addresses;
    }

    /**
     * The default transport address of the ActorSystem
     *
     * @return The listen address of the default transport
     */
    public Address defaultAddress() {
        return this.defaultAddress;
    }

    /**
     * Resolves the correct local address to be used for contacting the given remote address
     *
     * @param remote the remote address
     *
     * @return the local address to be used for the given remote address
     */
    public Address localAddressForRemote(Address remote) {
        String protocol = remote.protocol();
        if (protocol.equals(P2PActorRefProvider.SCHEME)) {
            return defaultAddress();
        }
        else {
            throw new P2PTransportException("Protocol '" + protocol + "' is not allowed. Please use '" + P2PActorRefProvider.SCHEME + "'");
        }
    }

    /**
     * Start up the transport, i.e. enable incoming connections.
     *
     * @return
     */
    public void start() {
        try {
            CompletableFuture<Void>[] startFutures = channels.stream().map(P2PTransportChannel::start).toArray(CompletableFuture[]::new);
            CompletableFuture.allOf(startFutures).get(startupTimeout.toSeconds(), TimeUnit.SECONDS);
            eventPublisher.notifyListeners(new P2PTransportListenEvent(defaultAddress));
        }
        catch (ExecutionException | InterruptedException e) {
            notifyError("Startup failed.", e);
            throw new P2PTransportException("Startup failed.", e);
        }
        catch (TimeoutException e) {
            notifyError("Startup timed out.", e);
            throw new P2PTransportException("Startup timed out.", e);
        }
    }

    /**
     * Sends the given message to the recipient supplying the sender() if any
     */
    public void send(Object message, Optional<ActorRef> senderOption, P2PActorRef recipient) {
        P2PTransportChannel channel = channels.stream().filter(b -> b.accept(recipient)).findFirst().orElse(null);

        if (channel != null) {
            log().debug("Send message using channel {}", channel);
            try {
                ActorRef sender = senderOption.orElse(ActorRef.noSender());
                OutboundMessageEnvelope outboundMessage = new OutboundMessageEnvelope(requireNonNull(message), sender, requireNonNull(recipient));
                channel.send(outboundMessage);
            }
            catch (P2PTransportChannelException e) {
                notifyError("Unable to send message: Channel failed", e);
                throw new P2PTransportException("Unable to send message: Channel failed", e);
            }
        }
        else {
            P2PTransportException e = new P2PTransportException("Unable to send message: No Channel is able to send this message to recipient " + recipient);
            notifyError("Unable to send message: No Channel is able to send this message to recipient", e);
            throw e;
        }
    }

    public void receive(InboundMessageEnvelope inboundMessage) {
        InternalActorRef recipient = inboundMessage.getAkkaRecipient();
        ActorRef sender = inboundMessage.getAkkaSender();
        Object message = inboundMessage.getDeserializedMessage();
        Address recipientAddress = inboundMessage.getAkkaRecipientAddress();

        if ((recipient instanceof LocalRef || recipient instanceof RepointableRef) && ((ActorRefScope) recipient).isLocal()) {
            log().debug("received message [{}] to [{}] from [{}]", message.getClass().getName(), recipient, sender);
            if (message instanceof ActorSelectionMessage) {
                ActorSelectionMessage selectionMessage = (ActorSelectionMessage) message;

                ActorSelection$.MODULE$.deliverSelection((InternalActorRef) recipient, sender, selectionMessage);
            }
            //        else if (message instanceof PossiblyHarmful && untrustedMode) {
            //            if (debugLogEnabled) {
            //                log.debug(
            //                        LogMarker.Security(),
            //                        "operating in UntrustedMode, dropping inbound PossiblyHarmful message of type [{}] to [{}] from [{}]",
            //                        message.getClass().getName(),
            //                        recipient,
            //                        sender
            //                );
            //            }
            //        }
            else if (message instanceof SystemMessage) {
                ((InternalActorRef) recipient).sendSystemMessage((SystemMessage) message);
            }
            else {
                recipient.tell(message, sender);
            }
        }
        else if ((recipient instanceof P2PActorRef || recipient instanceof RepointableRef) && !((ActorRefScope) recipient).isLocal() /* && !untrustedMode*/) {
            log().debug("remote-destined message");
            if (addresses().contains(recipientAddress)) {
                // if it was originally addressed to us but is in fact remote from our point of view (i.e. remote-deployed)
                recipient.tell(message, sender);
            }
            else {
                Set<String> printableAddresses = addresses().stream().map(Address::toString).collect(Collectors.toSet());
                log().error(
                        "dropping message [{}] for non-local recipient [{}] arriving at [{}] inbound addresses are [{}]",
                        message.getClass().getName(),
                        recipient,
                        recipientAddress,
                        String.join(", ", printableAddresses)
                );
            }
        }
        else {
            Set<String> printableAddresses = addresses().stream().map(Address::toString).collect(Collectors.toSet());
            log().error(
                    "dropping message [{}] for unknown recipient [{}] arriving at [{}] inbound addresses are [{}]",
                    message.getClass().getName(),
                    recipient,
                    recipientAddress,
                    String.join(", ", printableAddresses)
            );
        }
    }

    /**
     * Shuts down the remoting
     */
    public CompletableFuture<Void> shutdown() {
        return CompletableFuture.runAsync(() -> {
            try {
                CompletableFuture<Void>[] shutdownFutures = channels.stream().map(P2PTransportChannel::shutdown).toArray(CompletableFuture[]::new);
                CompletableFuture.allOf(shutdownFutures).get(shutdownTimeout.toSeconds(), TimeUnit.SECONDS);
                eventPublisher.notifyListeners(new P2PTransportShutdownEvent());
            }
            catch (InterruptedException | ExecutionException | TimeoutException e) {
                notifyError("Failure during shutdown of remoting.", e);
                throw new CompletionException(e);
            }
        });
    }

    /**
     * A Logger that can be used to log issues that may occur
     */
    public LoggingAdapter log() {
        return Logging.getLogger(system, this);
    }

    public P2PActorRefProvider getProvider() {
        return provider;
    }

    public void notifyError(String msg, Throwable cause) {
        eventPublisher.notifyListeners(new P2PTransportErrorEvent(new P2PTransportException(msg, cause)));
    }

    /**
     * Is called by the {@link P2PTransportChannel}s to inform about channel failures.
     *
     * @param channel
     * @param e
     */
    public void channelFailed(P2PTransportChannel channel, P2PTransportChannelException e) {
        notifyError("Transport channel " + channel + " failed.", e);
        e.printStackTrace();
        //throw new P2PTransportException("Transport channel " + channel + " failed.", e);
        // FIXME: do graceful actor system shutdown
        System.exit(1);
    }

    public class EventPublisher {
        private final ExtendedActorSystem system;
        private final LoggingAdapter log;
        private final int logLevel;

        public EventPublisher(ExtendedActorSystem system, LoggingAdapter log, int logLevel) {
            this.system = system;
            this.log = log;
            this.logLevel = logLevel;
        }

        public void notifyListeners(P2PTransportLifecycleEvent event) {
            system.eventStream().publish(event);

            if (event.logLevel() <= logLevel) {
                switch (event.logLevel()) {
                    case 1: // LogLevel.ErrorLevel
                        log.error("{}", event);
                        break;
                    case 2: // LogLevel.WarningLevel
                        log.warning("{}", event);
                        break;
                    case 3: // LogLevel.InfoLevel
                        log.info("{}", event);
                        break;
                    case 4: // LogLevel.DebugLevel
                        log.debug("{}", event);
                        break;
                }
            }
        }
    }
}
