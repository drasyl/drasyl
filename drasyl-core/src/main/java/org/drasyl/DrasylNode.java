/*
 * Copyright (c) 2020-2021 Heiko Bornholdt and Kevin Röbert
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.drasyl;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.EncoderException;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import org.drasyl.annotation.Beta;
import org.drasyl.annotation.NonNull;
import org.drasyl.annotation.Nullable;
import org.drasyl.channel.AddressedMessage;
import org.drasyl.channel.ApplicationMessageCodec;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.channel.MessageSerializer;
import org.drasyl.event.Event;
import org.drasyl.event.InboundExceptionEvent;
import org.drasyl.event.MessageEvent;
import org.drasyl.event.Node;
import org.drasyl.event.NodeDownEvent;
import org.drasyl.event.NodeNormalTerminationEvent;
import org.drasyl.event.NodeUnrecoverableErrorEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityManager;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.intravm.IntraVmDiscovery;
import org.drasyl.localhost.LocalHostDiscovery;
import org.drasyl.loopback.handler.LoopbackMessageHandler;
import org.drasyl.monitoring.Monitoring;
import org.drasyl.peer.PeersManagerHandler;
import org.drasyl.plugin.PluginManager;
import org.drasyl.remote.handler.ChunkingHandler;
import org.drasyl.remote.handler.HopCountGuard;
import org.drasyl.remote.handler.InternetDiscovery;
import org.drasyl.remote.handler.InvalidProofOfWorkFilter;
import org.drasyl.remote.handler.LocalNetworkDiscovery;
import org.drasyl.remote.handler.OtherNetworkFilter;
import org.drasyl.remote.handler.RateLimiter;
import org.drasyl.remote.handler.RemoteMessageToByteBufCodec;
import org.drasyl.remote.handler.StaticRoutesHandler;
import org.drasyl.remote.handler.UdpMulticastServer;
import org.drasyl.remote.handler.UdpServer;
import org.drasyl.remote.handler.crypto.ArmHandler;
import org.drasyl.remote.handler.portmapper.PortMapper;
import org.drasyl.remote.handler.tcp.TcpClient;
import org.drasyl.remote.handler.tcp.TcpServer;
import org.drasyl.remote.protocol.InvalidMessageFormatException;
import org.drasyl.remote.protocol.UnarmedMessage;
import org.drasyl.util.EventLoopGroupUtil;
import org.drasyl.util.FutureUtil;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;
import static org.drasyl.channel.Null.NULL;
import static org.drasyl.util.PlatformDependent.unsafeStaticFieldOffsetSupported;

/**
 * Represents a node in the drasyl Overlay Network. Applications that want to run on drasyl must
 * implement this class.
 * <p>
 * Example usage:
 * <pre><code>
 * DrasylNode node = new DrasylNode() {
 *   &#64;Override
 *   public void onEvent(Event event) {
 *     // handle incoming events (messages) here
 *     System.out.println("Event received: " + event);
 *   }
 * };
 * node.start();
 *
 * // wait till NodeOnlineEvent has been received
 *
 * // send message to another node
 * node.send("0229041b273dd5ee1c2bef2d77ae17dbd00d2f0a2e939e22d42ef1c4bf05147ea9", "Hello World");
 *
 * // shutdown node
 * node.shutdown();
 * </code></pre>
 */
@SuppressWarnings({ "java:S107", "java:S118" })
@Beta
public abstract class DrasylNode {
    private static final Logger LOG = LoggerFactory.getLogger(DrasylNode.class);
    private static String version;
    protected final Identity identity;
    protected final ServerBootstrap bootstrap;
    private ChannelFuture channelFuture;

    static {
        // https://github.com/netty/netty/issues/7817
        System.setProperty("io.netty.tryReflectionSetAccessible", "true");

        if (unsafeStaticFieldOffsetSupported()) {
            // dirty fix from Stack Overflow: https://stackoverflow.com/questions/46454995/how-to-hide-warning-illegal-reflective-access-in-java-9-without-jvm-argument/53517025#53517025
            try {
                final Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
                final Field field = unsafeClass.getDeclaredField("theUnsafe");
                field.setAccessible(true); // NOSONAR
                final Object unsafe = field.get(null);

                final Method putObjectVolatile = unsafeClass.getDeclaredMethod("putObjectVolatile", Object.class, long.class, Object.class);
                final Method staticFieldOffset = unsafeClass.getDeclaredMethod("staticFieldOffset", Field.class);

                final Class<?> loggerClass = Class.forName("jdk.internal.module.IllegalAccessLogger");
                final Field loggerField = loggerClass.getDeclaredField("logger");
                final Long offset = (Long) staticFieldOffset.invoke(unsafe, loggerField);
                putObjectVolatile.invoke(unsafe, loggerClass, offset, null);
            }
            catch (final Exception e) { // NOSONAR
                LOG.debug(e);
            }
        }
    }

    protected DrasylNode(final Identity identity,
                         final ServerBootstrap bootstrap,
                         final ChannelFuture channelFuture) {
        this.identity = requireNonNull(identity);
        this.bootstrap = requireNonNull(bootstrap);
        this.channelFuture = channelFuture;
    }

    /**
     * Creates a new drasyl Node with the given <code>config</code>. The node is only being created,
     * it neither connects to the Overlay * Network, nor can send or receive messages. To do this
     * you have to call {@link #start()}.
     * <p>
     * Note: This is a blocking method, because when a node is started for the first time, its
     * identity must be created. This can take up to a minute because of the proof of work.
     *
     * @param config custom configuration used for this node
     * @throws NullPointerException if {@code config} is {@code null}
     * @throws DrasylException      if identity could not be loaded or created
     */
    @SuppressWarnings({ "java:S2095" })
    protected DrasylNode(final DrasylConfig config) throws DrasylException {
        try {
            final IdentityManager identityManager = new IdentityManager(config);
            identityManager.loadOrCreateIdentity();
            identity = identityManager.getIdentity();
        }
        catch (final IOException e) {
            throw new DrasylException("Couldn't load or create identity", e);
        }

        bootstrap = new ServerBootstrap()
                .group(DrasylChannelEventLoopGroupUtil.getParentGroup(), DrasylChannelEventLoopGroupUtil.getChildGroup())
                .localAddress(identity)
                .channel(DrasylServerChannel.class)
                .handler(new DrasylNodeChannelInitializer(config, identity, this::onEvent))
                .childHandler(new DrasylNodeChildChannelInitializer(config, this::onEvent));

        LOG.debug("drasyl node with config `{}` and identity `{}` created", config, identity);
    }

    /**
     * Creates a new drasyl Node. The node is only being created, it neither connects to the Overlay
     * Network, nor can send or receive messages. To do this you have to call {@link #start()}.
     * <p>
     * Note: This is a blocking method, because when a node is started for the first time, its
     * identity must be created. This can take up to a minute because of the proof of work.
     *
     * @throws DrasylException       if identity could not be loaded or created
     * @throws DrasylConfigException if config is invalid
     */
    protected DrasylNode() throws DrasylException {
        this(DrasylConfig.of());
    }

    /**
     * Returns the version of the node. If the version could not be read, {@code null} is returned.
     *
     * @return the version of the node. If the version could not be read, {@code null} is returned
     */
    @SuppressWarnings("java:S2444")
    @Nullable
    public static String getVersion() {
        if (version == null) {
            final InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("META-INF/org.drasyl.versions.properties");
            if (inputStream != null) {
                try {
                    final Properties properties = new Properties();
                    properties.load(inputStream);
                    version = properties.getProperty("version");
                }
                catch (final IOException e) {
                    LOG.debug("Unable to read properties file.", e);
                }
            }
        }

        return version;
    }

    /**
     * Sends <code>event</code> to the application and tells it information about the local node,
     * other peers, connections or incoming messages.
     *
     * @param event the event
     */
    public abstract void onEvent(@NonNull Event event);

    /**
     * Sends the content of {@code payload} to the identity {@code recipient}. Returns a failed
     * future with a {@link IllegalStateException} if the message could not be sent to the recipient
     * or a super peer. Important: Just because the future did not fail does not automatically mean
     * that the message could be delivered. Delivery confirmations must be implemented by the
     * application.
     *
     * <p>
     * <b>Note</b>: It is possible that the passed object cannot be serialized. In this case it is
     * not sent and the future is fulfilled with an exception. Serializable objects can be added on
     * start via the {@link DrasylConfig}.
     * </p>
     *
     * @param recipient the recipient of a message as compressed public key
     * @param payload   the payload of a message
     * @return a completion stage if the message was successfully processed, otherwise an
     * exceptionally completion stage
     * @see MessageSerializer
     * @since 0.1.3
     */
    @NonNull
    public CompletionStage<Void> send(@NonNull final String recipient,
                                      @Nullable final Object payload) {
        try {
            return send(IdentityPublicKey.of(recipient), payload);
        }
        catch (final IllegalArgumentException e) {
            return failedFuture(new DrasylException("Recipient does not conform to a valid public key.", e));
        }
    }

    /**
     * Sends the content of {@code payload} to the identity {@code recipient}. Returns a failed
     * future with a {@link IllegalStateException} if the message could not be sent to the recipient
     * or a super peer. Important: Just because the future did not fail does not automatically mean
     * that the message could be delivered. Delivery confirmations must be implemented by the
     * application.
     *
     * <p>
     * <b>Note</b>: It is possible that the passed object cannot be serialized. In this case it is
     * not sent and the future is fulfilled with an exception. Serializable objects can be added on
     * start via the {@link DrasylConfig}.
     * </p>
     *
     * @param recipient the recipient of a message
     * @param payload   the payload of a message
     * @return a completion stage if the message was successfully processed, otherwise an
     * exceptionally completion stage
     * @see MessageSerializer
     * @since 0.1.3
     */
    @SuppressWarnings("ReplaceNullCheck")
    @NonNull
    public CompletionStage<Void> send(@NonNull final DrasylAddress recipient,
                                      @Nullable final Object payload) {
        if (channelFuture != null && channelFuture.channel().isOpen()) {
            final CompletableFuture<Void> future = new CompletableFuture<>();
            resolve(recipient).thenAccept(c -> {
                final Object p;
                if (payload == null) {
                    p = NULL;
                }
                else {
                    p = payload;
                }

                c.writeAndFlush(p).addListener(f -> {
                    if (f.isSuccess()) {
                        future.complete(null);
                    }
                    else {
                        future.completeExceptionally(f.cause());
                    }
                });
            });

            return future;
        }
        else {
            return failedFuture(new Exception("You have to call DrasylNode#start() first!"));
        }
    }

    /**
     * Creates a future containing a {@link Channel} for communication with {@code address}.
     * <p>
     * Note: be aware that the returned channel can be closed on inactivity according to {@link
     * DrasylConfig#getChannelInactivityTimeout()}. A closed channel can no longer be used. However,
     * a new channel can be created via this method.
     *
     * @param address peer address used for {@link Channel} creation
     * @return future containing {@link Channel} for {@code address} on completion
     */
    @NonNull
    public CompletionStage<Channel> resolve(@NonNull final DrasylAddress address) {
        if (channelFuture != null && channelFuture.channel().isOpen()) {
            final CompletableFuture<Channel> future = new CompletableFuture<>();
            channelFuture.channel().pipeline().fireUserEventTriggered(new Resolve(address, future));
            return future;
        }
        else {
            return failedFuture(new Exception("You have to call DrasylNode#start() first!"));
        }
    }

    /**
     * Creates a future containing a {@link Channel} for communication with {@code address}.
     * <p>
     * Note: be aware that the returned channel can be closed on inactivity according to {@link
     * DrasylConfig#getChannelInactivityTimeout()}. A closed channel can no longer be used. However,
     * a new channel can be created via this method.
     *
     * @param address peer address used for {@link Channel} creation
     * @return future containing {@link Channel} for {@code address} on completion
     */
    @NonNull
    public CompletionStage<Channel> resolve(@NonNull final String address) {
        try {
            return resolve(IdentityPublicKey.of(address));
        }
        catch (final IllegalArgumentException e) {
            return failedFuture(new DrasylException("address does not conform to a valid public key.", e));
        }
    }

    /**
     * Shut the drasyl node down.
     * <p>
     * If there is a connection to a Super Peer, our node will deregister from that Super Peer.
     * <p>
     * If the local server has been started, it will now be stopped.
     * <p>
     * This method does not stop the shared threads. To kill the shared threads, you have to call
     * the {@link EventLoopGroupUtil#shutdown()} method.
     * <p>
     *
     * @return this method returns a future, which complements if all shutdown steps have been
     * completed.
     */
    @NonNull
    @SuppressWarnings("java:S1905")
    public synchronized CompletableFuture<Void> shutdown() {
        if (channelFuture != null) {
            try {
                return FutureUtil.toFuture(channelFuture.channel().close());
            }
            finally {
                channelFuture = null;
            }
        }
        else {
            return completedFuture(null);
        }
    }

    /**
     * Start the drasyl node.
     * <p>
     * First, the identity of the node is loaded. If none exists, a new one is generated.
     * <p>
     * If activated, a local server is started. This allows other nodes to discover our node.
     * <p>
     * If a super peer has been configured, a client is started which connects to this super peer.
     * Our node uses the Super Peer to discover and communicate with other nodes.
     * <p>
     *
     * @return this method returns a future, which complements if all components necessary for the
     * operation have been started.
     */
    @NonNull
    @SuppressWarnings("java:S1905")
    public synchronized CompletableFuture<Void> start() {
        if (channelFuture == null) {
            channelFuture = bootstrap.bind();
            return FutureUtil.toFuture(channelFuture);
        }
        else {
            return completedFuture(null);
        }
    }

    /**
     * Returns the {@link ChannelPipeline} to allow users to add own handlers.
     *
     * @return the pipeline
     */
    @Nullable
    public ChannelPipeline pipeline() {
        if (channelFuture != null) {
            return channelFuture.channel().pipeline();
        }
        else {
            return null;
        }
    }

    /**
     * Returns the {@link Identity} of this node.
     *
     * @return the {@link Identity} of this node
     */
    @NonNull
    public Identity identity() {
        return identity;
    }

    /**
     * Signals {@link DrasylNodeChannelInitializer} to resolve a given {@link DrasylAddress} to a
     * {@link Channel}.
     */
    public static class Resolve {
        private final DrasylAddress recipient;
        private final CompletableFuture<Channel> future;

        public Resolve(final DrasylAddress recipient,
                       final CompletableFuture<Channel> future) {
            this.recipient = requireNonNull(recipient);
            this.future = requireNonNull(future);
        }

        public DrasylAddress recipient() {
            return recipient;
        }

        public CompletableFuture<Channel> future() {
            return future;
        }
    }

    /**
     * Initialize the {@link io.netty.channel.ServerChannel} used by {@link DrasylNode}.
     */
    public static class DrasylNodeChannelInitializer extends ChannelInitializer<Channel> {
        public static final String NODE_LIFECYCLE_HANDLER = "NODE_LIFECYCLE_HANDLER";
        public static final String CHANNEL_RESOLVER = "CHANNEL_RESOLVER";
        public static final String PLUGIN_MANAGER_HANDLER = "PLUGIN_MANAGER_HANDLER";
        public static final String PEERS_MANAGER_HANDLER = "PEERS_MANAGER_HANDLER";
        public static final String CHILD_CHANNEL_ROUTER = "CHILD_CHANNEL_ROUTER";
        public static final String APPLICATION_MESSAGE_CODEC = "APPLICATION_MESSAGE_CODEC";
        public static final String LOOPBACK_MESSAGE_HANDLER = "LOOPBACK_OUTBOUND_MESSAGE_SINK_HANDLER";
        public static final String INTRA_VM_DISCOVERY = "INTRA_VM_DISCOVERY";
        public static final String STATIC_ROUTES_HANDLER = "STATIC_ROUTES_HANDLER";
        public static final String LOCAL_HOST_DISCOVERY = "LOCAL_HOST_DISCOVERY";
        public static final String INTERNET_DISCOVERY = "INTERNET_DISCOVERY";
        public static final String LOCAL_NETWORK_DISCOVER = "LOCAL_NETWORK_DISCOVER";
        public static final String HOP_COUNT_GUARD = "HOP_COUNT_GUARD";
        public static final String MONITORING_HANDLER = "MONITORING_HANDLER";
        public static final String RATE_LIMITER = "RATE_LIMITER";
        public static final String UNARMED_MESSAGE_READER = "UNARMED_MESSAGE_READER";
        public static final String ARM_HANDLER = "ARM_HANDLER";
        public static final String INVALID_PROOF_OF_WORK_FILTER = "INVALID_PROOF_OF_WORK_FILTER";
        public static final String OTHER_NETWORK_FILTER = "OTHER_NETWORK_FILTER";
        public static final String CHUNKING_HANDLER = "CHUNKING_HANDLER";
        public static final String REMOTE_MESSAGE_TO_BYTE_BUF_CODEC = "REMOTE_MESSAGE_TO_BYTE_BUF_CODEC";
        public static final String UDP_MULTICAST_SERVER = "UDP_MULTICAST_SERVER";
        public static final String TCP_SERVER = "TCP_SERVER";
        public static final String TCP_CLIENT = "TCP_CLIENT";
        public static final String PORT_MAPPER = "PORT_MAPPER";
        public static final String UDP_SERVER = "UDP_SERVER";
        private final DrasylConfig config;
        private final Identity identity;
        private final Consumer<Event> eventConsumer;
        private boolean errorOccurred;

        public DrasylNodeChannelInitializer(final DrasylConfig config,
                                            final Identity identity,
                                            final Consumer<Event> eventConsumer,
                                            final boolean errorOccurred) {
            this.config = requireNonNull(config);
            this.identity = requireNonNull(identity);
            this.errorOccurred = errorOccurred;
            this.eventConsumer = requireNonNull(eventConsumer);
        }

        public DrasylNodeChannelInitializer(final DrasylConfig config,
                                            final Identity identity,
                                            final Consumer<Event> eventConsumer) {
            this(config, identity, eventConsumer, false);
        }

        @SuppressWarnings("java:S1188")
        @Override
        protected void initChannel(final Channel ch) {
            final PluginManager pluginManager = new PluginManager(config, identity);

            ch.pipeline().addFirst(NODE_LIFECYCLE_HANDLER, new NodeLifecycleHandler(ch));

            ch.pipeline().addFirst(CHANNEL_RESOLVER, new ChannelResolver());

            ch.pipeline().addFirst(PLUGIN_MANAGER_HANDLER, new PluginManagerHandler(pluginManager));

            ch.pipeline().addFirst(PEERS_MANAGER_HANDLER, new PeersManagerHandler(identity));

            ch.pipeline().addFirst(CHILD_CHANNEL_ROUTER, new ChildChannelRouter());

            // convert ByteString <-> ApplicationMessage
            ch.pipeline().addFirst(APPLICATION_MESSAGE_CODEC, new ApplicationMessageCodec(this.config.getNetworkId(), this.identity.getIdentityPublicKey(), this.identity.getProofOfWork()));

            // convert outbound messages addresses to us to inbound messages
            ch.pipeline().addFirst(LOOPBACK_MESSAGE_HANDLER, new LoopbackMessageHandler(this.identity.getAddress()));

            // discover nodes running within the same jvm
            if (this.config.isIntraVmDiscoveryEnabled()) {
                ch.pipeline().addFirst(INTRA_VM_DISCOVERY, new IntraVmDiscovery(this.config.getNetworkId(), this.identity.getAddress()));
            }

            if (this.config.isRemoteEnabled()) {
                // route outbound messages to pre-configured ip addresses
                if (!this.config.getRemoteStaticRoutes().isEmpty()) {
                    ch.pipeline().addFirst(STATIC_ROUTES_HANDLER, new StaticRoutesHandler(this.config.getRemoteStaticRoutes()));
                }

                if (this.config.isRemoteLocalHostDiscoveryEnabled()) {
                    // discover nodes running on the same local computer
                    ch.pipeline().addFirst(LOCAL_HOST_DISCOVERY, new LocalHostDiscovery(
                            this.config.getNetworkId(),
                            this.config.isRemoteLocalHostDiscoveryWatchEnabled(),
                            this.config.getRemoteBindHost(),
                            this.config.getRemoteLocalHostDiscoveryLeaseTime(),
                            this.config.getRemoteLocalHostDiscoveryPath(),
                            this.identity.getAddress()
                    ));
                }

                // discovery nodes on the local network
                if (this.config.isRemoteLocalNetworkDiscoveryEnabled()) {
                    ch.pipeline().addFirst(LOCAL_NETWORK_DISCOVER, new LocalNetworkDiscovery(
                            this.config.getNetworkId(),
                            this.config.getRemotePingInterval(),
                            this.config.getRemotePingTimeout(),
                            this.identity.getAddress(),
                            this.identity.getProofOfWork()
                    ));
                }

                // discover nodes on the internet
                ch.pipeline().addFirst(INTERNET_DISCOVERY, new InternetDiscovery(
                        this.config.getNetworkId(),
                        this.config.getRemotePingMaxPeers(),
                        this.config.getRemotePingInterval(),
                        this.config.getRemotePingTimeout(),
                        this.config.getRemotePingCommunicationTimeout(),
                        this.config.isRemoteSuperPeerEnabled(),
                        this.config.getRemoteSuperPeerEndpoints(),
                        this.config.getRemoteUniteMinInterval(),
                        this.identity.getAddress(),
                        this.identity.getProofOfWork()
                ));

                // outbound message guards
                ch.pipeline().addFirst(HOP_COUNT_GUARD, new HopCountGuard(this.config.getRemoteMessageHopLimit()));

                if (this.config.isMonitoringEnabled()) {
                    ch.pipeline().addFirst(MONITORING_HANDLER, new Monitoring(
                            this.config.getMonitoringHostTag(),
                            this.config.getMonitoringInfluxUri(),
                            this.config.getMonitoringInfluxUser(),
                            this.config.getMonitoringInfluxPassword(),
                            this.config.getMonitoringInfluxDatabase(),
                            this.config.getMonitoringInfluxReportingFrequency()
                    ));
                }

                ch.pipeline().addFirst(RATE_LIMITER, new RateLimiter(this.identity.getAddress()));

                ch.pipeline().addFirst(UNARMED_MESSAGE_READER, new SimpleChannelInboundHandler<AddressedMessage<UnarmedMessage, ?>>(false) {
                    @Override
                    public boolean acceptInboundMessage(final Object msg) {
                        return msg instanceof AddressedMessage && ((AddressedMessage<?, ?>) msg).message() instanceof UnarmedMessage;
                    }

                    @Override
                    protected void channelRead0(final ChannelHandlerContext ctx,
                                                final AddressedMessage<UnarmedMessage, ?> msg) throws InvalidMessageFormatException {
                        ctx.fireChannelRead(new AddressedMessage<>(msg.message().read(), msg.address()));
                    }
                });

                // arm outbound and disarm inbound messages
                if (this.config.isRemoteMessageArmEnabled()) {
                    ch.pipeline().addFirst(ARM_HANDLER, new ArmHandler(
                            this.config.getNetworkId(),
                            this.config.getRemoteMessageArmSessionMaxCount(),
                            this.config.getRemoteMessageArmSessionMaxAgreements(),
                            this.config.getRemoteMessageArmSessionExpireAfter(),
                            this.config.getRemoteMessageArmSessionRetryInterval(),
                            this.identity
                    ));
                }

                // filter out inbound messages with invalid proof of work or other network id
                ch.pipeline().addFirst(INVALID_PROOF_OF_WORK_FILTER, new InvalidProofOfWorkFilter(this.identity.getAddress()));
                ch.pipeline().addFirst(OTHER_NETWORK_FILTER, new OtherNetworkFilter(this.config.getNetworkId()));

                // split messages too big for udp
                ch.pipeline().addFirst(CHUNKING_HANDLER, new ChunkingHandler(
                        this.config.getRemoteMessageMaxContentLength(),
                        this.config.getRemoteMessageMtu(),
                        this.config.getRemoteMessageComposedMessageTransferTimeout(),
                        this.identity.getAddress()
                ));

                // convert RemoteMessage <-> ByteBuf
                ch.pipeline().addFirst(REMOTE_MESSAGE_TO_BYTE_BUF_CODEC, RemoteMessageToByteBufCodec.INSTANCE);

                // multicast server (lan discovery)
                if (this.config.isRemoteLocalNetworkDiscoveryEnabled()) {
                    ch.pipeline().addFirst(UDP_MULTICAST_SERVER, new UdpMulticastServer(this.identity.getAddress()));
                }

                // tcp fallback
                if (this.config.isRemoteTcpFallbackEnabled()) {
                    if (!this.config.isRemoteSuperPeerEnabled()) {
                        ch.pipeline().addFirst(TCP_SERVER, new TcpServer(
                                this.config.getRemoteTcpFallbackServerBindHost(),
                                this.config.getRemoteTcpFallbackServerBindPort(),
                                this.config.getRemotePingTimeout()
                        ));
                    }
                    else {
                        ch.pipeline().addFirst(TCP_CLIENT, new TcpClient(
                                this.config.getRemoteSuperPeerEndpoints(),
                                this.config.getRemoteTcpFallbackClientTimeout(),
                                this.config.getRemoteTcpFallbackClientAddress()
                        ));
                    }
                }

                // port mapping (PCP, NAT-PMP, UPnP-IGD, etc.)
                if (this.config.isRemoteExposeEnabled()) {
                    ch.pipeline().addFirst(PORT_MAPPER, new PortMapper(this.identity.getAddress()));
                }

                // udp server
                ch.pipeline().addFirst(UDP_SERVER, new UdpServer(
                        this.identity.getAddress(),
                        this.config.getRemoteBindHost(),
                        this.config.getRemoteBindPort()
                ));
            }
        }

        private static class PluginManagerHandler extends ChannelInboundHandlerAdapter {
            private final PluginManager pluginManager;

            public PluginManagerHandler(final PluginManager pluginManager) {
                this.pluginManager = pluginManager;
            }

            @Override
            public void channelRegistered(final ChannelHandlerContext ctx) throws Exception {
                super.channelRegistered(ctx);

                pluginManager.beforeStart(ctx);
            }

            @Override
            public void channelActive(final ChannelHandlerContext ctx) throws Exception {
                super.channelActive(ctx);

                pluginManager.afterStart(ctx);
            }

            @Override
            public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
                super.channelInactive(ctx);

                pluginManager.beforeShutdown(ctx);
            }

            @Override
            public void channelUnregistered(final ChannelHandlerContext ctx) throws Exception {
                super.channelUnregistered(ctx);

                pluginManager.afterShutdown(ctx);
            }
        }

        private class NodeLifecycleHandler extends ChannelInboundHandlerAdapter {
            private final Channel ch;

            public NodeLifecycleHandler(final Channel ch) {
                this.ch = ch;
            }

            @Override
            public void channelActive(final ChannelHandlerContext ctx) throws Exception {
                super.channelActive(ctx);

                LOG.info("Start drasyl node with identity `{}`...", ctx.channel().localAddress());
                userEventTriggered(ctx, NodeUpEvent.of(Node.of(identity)));
                LOG.info("drasyl node with identity `{}` has started", ctx.channel().localAddress());
            }

            @Override
            public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
                super.channelInactive(ctx);

                if (!errorOccurred) {
                    LOG.info("Shutdown drasyl node with identity `{}`...", ctx.channel().localAddress());
                    userEventTriggered(ctx, NodeDownEvent.of(Node.of(identity)));
                    userEventTriggered(ctx, NodeNormalTerminationEvent.of(Node.of(identity)));
                    LOG.info("drasyl node with identity `{}` has shut down", ctx.channel().localAddress());
                }
            }

            @SuppressWarnings("java:S2221")
            @Override
            public void userEventTriggered(final ChannelHandlerContext ctx,
                                           final Object evt) {
                if (evt instanceof Event) {
                    if (evt instanceof NodeUnrecoverableErrorEvent) {
                        errorOccurred = true;
                    }

                    ctx.executor().execute(() -> eventConsumer.accept((Event) evt));
                }

                // drop all other events
            }

            @Override
            public void exceptionCaught(final ChannelHandlerContext ctx,
                                        final Throwable e) {
                if (e instanceof UdpServer.BindFailedException || e instanceof TcpServer.BindFailedException) {
                    LOG.warn("drasyl node faced unrecoverable error and must shut down:", e);
                    userEventTriggered(ctx, NodeUnrecoverableErrorEvent.of(Node.of(identity), e));
                    ch.close();
                }
                else if (e instanceof EncoderException) {
                    LOG.error(e);
                }
                else {
                    userEventTriggered(ctx, InboundExceptionEvent.of(e));
                }
            }
        }

        /**
         * Routes inbound messages to the correct child channel and broadcast events to all child
         * channels.
         */
        private static class ChildChannelRouter extends SimpleChannelInboundHandler<AddressedMessage<?, IdentityPublicKey>> {
            public ChildChannelRouter() {
                super(false);
            }

            @Override
            public boolean acceptInboundMessage(final Object msg) throws Exception {
                return msg instanceof AddressedMessage && ((AddressedMessage<?, ?>) msg).address() instanceof IdentityPublicKey;
            }

            @Override
            protected void channelRead0(final ChannelHandlerContext ctx,
                                        final AddressedMessage<?, IdentityPublicKey> msg) {
                Object o = msg.message();
                final IdentityPublicKey sender = msg.address();

                // create/get channel
                final Channel channel = ((DrasylServerChannel) ctx.channel()).getOrCreateChildChannel(ctx, sender);

                if (o == null) {
                    o = NULL;
                }

                // pass message to channel
                channel.pipeline().fireChannelRead(o);
            }
        }

        private static class ChannelResolver extends ChannelInboundHandlerAdapter {
            @SuppressWarnings("java:S2221")
            @Override
            public void userEventTriggered(final ChannelHandlerContext ctx,
                                           final Object evt) {
                if (evt instanceof Resolve) {
                    final Resolve e = (Resolve) evt;
                    final IdentityPublicKey recipient = (IdentityPublicKey) e.recipient();
                    final CompletableFuture<Channel> future = e.future();
                    final Channel resolvedChannel = ((DrasylServerChannel) ctx.channel()).getOrCreateChildChannel(ctx, recipient);
                    resolvedChannel.eventLoop().execute(() -> future.complete(resolvedChannel));
                }
                else {
                    ctx.fireUserEventTriggered(evt);
                }
            }
        }
    }

    /**
     * Initialize child {@link Channel}s used by {@link DrasylNode}.
     */
    public static class DrasylNodeChildChannelInitializer extends ChannelInitializer<Channel> {
        public static final String CHILD_CHANNEL_TAIL = "CHILD_CHANNEL_TAIL";
        public static final String MESSAGE_SERIALIZER = "MESSAGE_SERIALIZER";
        public static final String INACTIVITY_CLOSER = "INACTIVITY_CLOSER";
        public static final String INACTIVITY_DETECTOR = "INACTIVITY_DETECTOR";
        private final DrasylConfig config;
        private final Consumer<Event> onEvent;

        public DrasylNodeChildChannelInitializer(final DrasylConfig config,
                                                 final Consumer<Event> onEvent) {
            this.onEvent = requireNonNull(onEvent);
            this.config = requireNonNull(config);
        }

        @Override
        protected void initChannel(final Channel ch) {
            // emit MessageEvents for every inbound message
            ch.pipeline().addFirst(CHILD_CHANNEL_TAIL, new ChannelInboundHandlerAdapter() {
                @Override
                public void channelRead(final ChannelHandlerContext ctx,
                                        Object msg) {
                    if (msg == NULL) {
                        msg = null;
                    }

                    final MessageEvent event = MessageEvent.of((IdentityPublicKey) ctx.channel().remoteAddress(), msg);
                    onEvent.accept(event);
                }

                @Override
                public void exceptionCaught(final ChannelHandlerContext ctx,
                                            final Throwable e) {
                    if (e instanceof EncoderException) {
                        LOG.error(e);
                    }
                    else {
                        onEvent.accept(InboundExceptionEvent.of(e));
                    }
                }
            });

            // convert Object <-> ByteString
            ch.pipeline().addFirst(MESSAGE_SERIALIZER, new MessageSerializer(config));

            // close inactive channels (to free up resources)
            final int inactivityTimeout = (int) config.getChannelInactivityTimeout().getSeconds();
            if (inactivityTimeout > 0) {
                ch.pipeline().addFirst(INACTIVITY_CLOSER, new ChannelInboundHandlerAdapter() {
                    @Override
                    public void userEventTriggered(final ChannelHandlerContext ctx,
                                                   final Object evt) throws Exception {
                        if (evt instanceof IdleStateEvent) {
                            final IdleStateEvent e = (IdleStateEvent) evt;
                            if (e.state() == IdleState.ALL_IDLE) {
                                LOG.debug("Close channel to {} due to inactivity.", ctx.channel()::remoteAddress);
                                ctx.close();
                            }
                        }
                        else {
                            super.userEventTriggered(ctx, evt);
                        }
                    }
                });
                ch.pipeline().addFirst(INACTIVITY_DETECTOR, new IdleStateHandler(0, 0, inactivityTimeout));
            }
        }
    }
}
