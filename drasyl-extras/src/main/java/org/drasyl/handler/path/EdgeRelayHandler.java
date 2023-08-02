package org.drasyl.handler.path;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.drasyl.channel.OverlayAddressedMessage;
import org.drasyl.handler.remote.protocol.ApplicationMessage;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import static java.util.Objects.requireNonNull;

public class EdgeRelayHandler extends ChannelDuplexHandler {
    private static final Logger LOG = LoggerFactory.getLogger(EdgeRelayHandler.class);
    private final IdentityPublicKey peer;
    private final IdentityPublicKey relay;

    public EdgeRelayHandler(final IdentityPublicKey peer, final IdentityPublicKey relay) {
        this.peer = requireNonNull(peer);
        this.relay = requireNonNull(relay);
    }

    //@Override
    //public void channelActive(final ChannelHandlerContext ctx) {
    //    super.channelActive(ctx);
    //}

    //@Override
    //public void handlerAdded(final ChannelHandlerContext ctx) {
    //    super.handlerAdded(ctx);
    //}

    @SuppressWarnings("unchecked")
    @Override
    public void write(final ChannelHandlerContext ctx,
                      final Object msg,
                      final ChannelPromise promise) {
        if (isApplicationMessageToPeer(msg)) {
            final OverlayAddressedMessage<ApplicationMessage> originalMsg = (OverlayAddressedMessage<ApplicationMessage>) msg;
            final OverlayAddressedMessage<ApplicationMessage> relayedMsg = new OverlayAddressedMessage<>(originalMsg.content(), relay, originalMsg.sender());
            LOG.error("Relay message `{}` for {} through {}.", originalMsg, originalMsg.recipient(), relay);
            ctx.write(relayedMsg, promise);
        }
    }

    private boolean isApplicationMessageToPeer(Object msg) {
        return msg instanceof OverlayAddressedMessage &&
                (((OverlayAddressedMessage<?>) msg).content()) instanceof ApplicationMessage &&
                peer.equals(((ApplicationMessage) ((OverlayAddressedMessage<?>) msg).content()).getRecipient());
    }
}
