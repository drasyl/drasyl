package org.drasyl.handler.path;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.drasyl.channel.OverlayAddressedMessage;
import org.drasyl.handler.remote.protocol.ApplicationMessage;
import org.drasyl.identity.IdentityPublicKey;

import static java.util.Objects.requireNonNull;

public class EdgeRelayHandler extends ChannelDuplexHandler {
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

    @Override
    public void write(final ChannelHandlerContext ctx,
                      final Object msg,
                      final ChannelPromise promise) {
        if (msg instanceof OverlayAddressedMessage && ((OverlayAddressedMessage<?>) msg).content() instanceof ApplicationMessage) {
            ctx.write(msg, promise);
        }
    }
}
