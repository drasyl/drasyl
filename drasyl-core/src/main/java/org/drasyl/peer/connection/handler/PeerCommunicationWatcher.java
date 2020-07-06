package org.drasyl.peer.connection.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.peer.connection.message.ApplicationMessage;

import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

/**
 * This handler informs when outgoing communication with other peers occurs. For this purpose,
 * {@link #peerCommunicationConsumer} is called for each {@link ApplicationMessage} that has been
 * sent from the local node to other peers.
 */
public class PeerCommunicationWatcher extends SimpleChannelOutboundHandler<ApplicationMessage> {
    public static final String PEER_COMMUNICATION_WATCHER = "peerCommunicationWatcher";
    private final Consumer<CompressedPublicKey> peerCommunicationConsumer;
    private final CompressedPublicKey ownPublicKey;

    public PeerCommunicationWatcher(CompressedPublicKey ownPublicKey,
                                    Consumer<CompressedPublicKey> peerCommunicationConsumer) {
        this.peerCommunicationConsumer = requireNonNull(peerCommunicationConsumer);
        this.ownPublicKey = requireNonNull(ownPublicKey);
    }

    @Override
    protected void channelWrite0(ChannelHandlerContext ctx,
                                 ApplicationMessage msg, ChannelPromise promise) {
        // passthrough message
        ctx.writeAndFlush(msg, promise);

        // inform about communication with recipient
        ctx.executor().submit(() -> {
            if (ownPublicKey.equals(msg.getSender())) {
                peerCommunicationConsumer.accept(msg.getRecipient());
            }
        }).addListener(future -> {
            Throwable cause = future.cause();
            if (cause != null) {
                ctx.fireExceptionCaught(cause);
            }
        });
    }
}
