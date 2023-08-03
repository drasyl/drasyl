package org.drasyl.handler.noop;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.drasyl.channel.OverlayAddressedMessage;
import org.drasyl.identity.IdentityPublicKey;

import static org.drasyl.handler.noop.NoopDiscardHandler.NOOP_MAGIC_NUMBER;

public class NoopDiscardServerHandler extends SimpleChannelInboundHandler<OverlayAddressedMessage<ByteBuf>> {
    @Override
    public boolean acceptInboundMessage(final Object msg) {
        return msg instanceof OverlayAddressedMessage<?> && ((OverlayAddressedMessage<?>) msg).content() instanceof ByteBuf;
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx,
                                final OverlayAddressedMessage<ByteBuf> msg) {
        if (msg.content().readableBytes() == Long.BYTES) {
            msg.content().markReaderIndex();

            if (msg.content().readLong() == NOOP_MAGIC_NUMBER) {
                msg.content().release();
                return;
            }
            else {
                msg.content().resetReaderIndex();
            }
        }

        ctx.fireChannelRead(msg);
    }
}
