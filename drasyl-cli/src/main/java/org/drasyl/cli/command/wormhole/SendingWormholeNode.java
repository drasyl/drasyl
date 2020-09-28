package org.drasyl.cli.command.wormhole;

import org.drasyl.DrasylConfig;
import org.drasyl.DrasylException;
import org.drasyl.DrasylNode;
import org.drasyl.DrasylNodeComponent;
import org.drasyl.crypto.Crypto;
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

import java.io.PrintStream;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@SuppressWarnings({ "java:S107" })
public class SendingWormholeNode extends DrasylNode {
    private final CompletableFuture<Void> doneFuture;
    private final PrintStream printStream;
    private final String password;
    private final AtomicBoolean sent;
    private String text;

    SendingWormholeNode(CompletableFuture<Void> doneFuture,
                        PrintStream printStream,
                        String password,
                        AtomicBoolean sent,
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
        this.password = password;
        this.sent = sent;
    }

    public SendingWormholeNode(DrasylConfig config,
                               PrintStream printStream) throws DrasylException {
        super(DrasylConfig.newBuilder(config).serverBindPort(0).marshallingAllowedTypes(List.of(WormholeMessage.class.getName())).build());
        this.doneFuture = new CompletableFuture<>();
        this.printStream = printStream;
        this.password = Crypto.randomString(16);
        this.sent = new AtomicBoolean();
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
            CompressedPublicKey receiver = ((MessageEvent) event).getSender();
            Object message = ((MessageEvent) event).getPayload();

            if (message instanceof PasswordMessage) {
                if (text != null && password.equals(((PasswordMessage) message).getPassword()) && sent.compareAndSet(false, true)) {
                    sendText(receiver);
                }
                else {
                    wrongPassword(receiver);
                }
            }
        }
    }

    private void sendText(CompressedPublicKey receiver) {
        send(receiver, new TextMessage(text)).whenComplete((result, e) -> {
            if (e != null) {
                doneFuture.completeExceptionally(e);
            }
            else {
                printStream.println("text message sent");
                doneFuture.complete(null);
            }
        });
    }

    private void wrongPassword(CompressedPublicKey receiver) {
        send(receiver, new WrongPasswordMessage()).whenComplete((result, e) -> {
            if (e != null) {
                doneFuture.completeExceptionally(e);
            }
            else {
                doneFuture.completeExceptionally(new Exception(
                        "Code confirmation failed. Either you or your correspondent\n" +
                                "typed the code wrong, or a would-be man-in-the-middle attacker guessed\n" +
                                "incorrectly. You could try again, giving both your correspondent and\n" +
                                "the attacker another chance."
                ));
            }
        });
    }

    public CompletableFuture<Void> doneFuture() {
        return doneFuture;
    }

    public void setText(String text) {
        this.text = text;
        String code = identity().getPublicKey().toString() + password;
        printStream.println("Wormhole code is: " + code);
        printStream.println("On the other computer, please run:");
        printStream.println();
        printStream.println("drasyl wormhole receive " + code);
        printStream.println();
    }
}
