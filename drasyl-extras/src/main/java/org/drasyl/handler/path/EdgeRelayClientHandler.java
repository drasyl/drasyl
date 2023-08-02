package org.drasyl.handler.path;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.channel.OverlayAddressedMessage;
import org.drasyl.handler.remote.protocol.ApplicationMessage;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import static java.util.Objects.requireNonNull;
import static org.drasyl.handler.noop.NoopDiscardHandler.NOOP_MAGIC_NUMBER;

public class EdgeRelayClientHandler extends ChannelDuplexHandler {
    private static final Logger LOG = LoggerFactory.getLogger(EdgeRelayClientHandler.class);
    private final IdentityPublicKey peer;
    private final IdentityPublicKey relay;

    public EdgeRelayClientHandler(final IdentityPublicKey peer,
                                  final IdentityPublicKey relay) {
        this.peer = requireNonNull(peer);
        this.relay = requireNonNull(relay);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void write(final ChannelHandlerContext ctx,
                      final Object msg,
                      final ChannelPromise promise) {
        if (isApplicationMessageToPeer(msg)) {
            // message for peer whose communication is to go through a relay
            final OverlayAddressedMessage<ApplicationMessage> originalMsg = (OverlayAddressedMessage<ApplicationMessage>) msg;

            if (!((DrasylServerChannel) ctx.channel()).isDirectPathPresent(relay)) {
                // we do not have a direct connection the relay yet, we should send a noop message to trigger direct link establishment
                final ByteBuf byteBuf = ctx.alloc().buffer(Long.BYTES).writeLong(NOOP_MAGIC_NUMBER);
                final OverlayAddressedMessage<ByteBuf> noopMsg = new OverlayAddressedMessage<>(byteBuf, relay, (DrasylAddress) ctx.channel().localAddress());
                LOG.debug("Send no-op message to `{}` to trigger direct path establishment.", relay);
                ctx.channel().write(noopMsg).addListener((ChannelFutureListener) channelFuture -> {
                    if (channelFuture.cause() != null) {
                        LOG.warn("Error sending NOOP: ", channelFuture.cause());
                    }
                });
            }

            // replace peer's address with the one of the relay
            final OverlayAddressedMessage<ApplicationMessage> relayedMsg = new OverlayAddressedMessage<>(originalMsg.content(), relay, originalMsg.sender());
            LOG.debug("Relay message `{}` for {} through {}.", originalMsg.content().getNonce(), originalMsg.recipient(), relay);
            ctx.write(relayedMsg, promise);
        }
        else {
            // we're not interested in this message, pass through
            ctx.write(msg, promise);
        }
    }

    private boolean isApplicationMessageToPeer(Object msg) {
        return msg instanceof OverlayAddressedMessage &&
                (((OverlayAddressedMessage<?>) msg).content()) instanceof ApplicationMessage &&
                peer.equals(((ApplicationMessage) ((OverlayAddressedMessage<?>) msg).content()).getRecipient());
    }
}
