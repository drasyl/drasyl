package org.drasyl.cli.command.wormhole;

import io.reactivex.rxjava3.disposables.Disposable;
import org.drasyl.DrasylConfig;
import org.drasyl.DrasylException;
import org.drasyl.DrasylNode;
import org.drasyl.DrasylNodeComponent;
import org.drasyl.event.Event;
import org.drasyl.event.MessageEvent;
import org.drasyl.event.NodeNormalTerminationEvent;
import org.drasyl.event.NodeUnrecoverableErrorEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.messenger.Messenger;
import org.drasyl.peer.Endpoint;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.PeerChannelGroup;
import org.drasyl.pipeline.DrasylPipeline;
import org.drasyl.plugins.PluginManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintStream;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.drasyl.util.DrasylScheduler.getInstanceLight;
import static org.drasyl.util.SecretUtil.maskSecret;

@SuppressWarnings({ "java:S107" })
public class ReceivingWormholeNode extends DrasylNode {
    private static final Logger log = LoggerFactory.getLogger(ReceivingWormholeNode.class);
    private final CompletableFuture<Void> doneFuture;
    private final PrintStream printStream;
    private final AtomicBoolean received;
    private CompressedPublicKey sender;
    private Disposable timeoutGuard;

    ReceivingWormholeNode(CompletableFuture<Void> doneFuture,
                          PrintStream printStream,
                          AtomicBoolean received,
                          CompressedPublicKey sender,
                          Disposable timeoutGuard,
                          DrasylConfig config,
                          Identity identity,
                          PeersManager peersManager,
                          PeerChannelGroup channelGroup,
                          Messenger messenger,
                          Set<Endpoint> endpoints,
                          AtomicBoolean acceptNewConnections,
                          DrasylPipeline pipeline,
                          List<DrasylNodeComponent> components,
                          PluginManager pluginManager,
                          AtomicBoolean started,
                          CompletableFuture<Void> startSequence,
                          CompletableFuture<Void> shutdownSequence) {
        super(config, identity, peersManager, channelGroup, messenger, endpoints, acceptNewConnections, pipeline, components, pluginManager, started, startSequence, shutdownSequence);
        this.doneFuture = doneFuture;
        this.printStream = printStream;
        this.received = received;
        this.sender = sender;
        this.timeoutGuard = timeoutGuard;
    }

    public ReceivingWormholeNode(DrasylConfig config,
                                 PrintStream printStream) throws DrasylException {
        super(DrasylConfig.newBuilder(config)
                .serverBindPort(0)
                .marshallingInboundAllowedTypes(List.of(WormholeMessage.class.getName()))
                .marshallingOutboundAllowedTypes(List.of(WormholeMessage.class.getName()))
                .build());
        this.doneFuture = new CompletableFuture<>();
        this.printStream = printStream;
        this.received = new AtomicBoolean();
    }

    @Override
    public void onEvent(Event event) {
        if (event instanceof NodeUnrecoverableErrorEvent) {
            doneFuture.completeExceptionally(((NodeUnrecoverableErrorEvent) event).getError());
        }
        else if (event instanceof NodeNormalTerminationEvent) {
            doneFuture.complete(null);
        }
        else if (event instanceof MessageEvent) {
            CompressedPublicKey messageSender = ((MessageEvent) event).getSender();
            Object message = ((MessageEvent) event).getPayload();

            if (message instanceof TextMessage && messageSender.equals(this.sender) && received.compareAndSet(false, true)) {
                receivedText((TextMessage) message);
            }
            else if (message instanceof WrongPasswordMessage && messageSender.equals(this.sender) && received.compareAndSet(false, true)) {
                fail();
            }
        }
    }

    @Override
    public CompletableFuture<Void> shutdown() {
        if (timeoutGuard != null && !timeoutGuard.isDisposed()) {
            timeoutGuard.dispose();
        }
        return super.shutdown();
    }

    private void receivedText(TextMessage message) {
        printStream.println(message.getText());
        doneFuture.complete(null);
    }

    private void fail() {
        doneFuture.completeExceptionally(new Exception(
                "Code confirmation failed. Either you or your correspondent\n" +
                        "typed the code wrong, or a would-be man-in-the-middle attacker guessed\n" +
                        "incorrectly. You could try again, giving both your correspondent and\n" +
                        "the attacker another chance."
        ));
    }

    public CompletableFuture<Void> doneFuture() {
        return doneFuture;
    }

    public void requestText(CompressedPublicKey sender, String password) {
        this.sender = sender;
        requestText(sender, password, new AtomicInteger(10));
    }

    public void requestText(CompressedPublicKey sender,
                            String password,
                            AtomicInteger remainingRetries) {
        if (log.isDebugEnabled()) {
            log.debug("Requesting text from '{}' with password '{}'", sender, maskSecret(password));
        }
        send(sender, new PasswordMessage(password)).whenComplete((result, e) -> {
            if (e != null) {
                if (remainingRetries.decrementAndGet() > 0) {
                    getInstanceLight().scheduleDirect(() -> requestText(sender, password, remainingRetries), 1, SECONDS);
                }
                else {
                    fail();
                }
            }
            else {
                timeoutGuard = getInstanceLight().scheduleDirect(this::fail, 10, SECONDS);
            }
        });
    }
}
