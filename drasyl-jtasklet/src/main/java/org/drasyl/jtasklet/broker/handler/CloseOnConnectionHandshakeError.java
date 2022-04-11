package org.drasyl.jtasklet.broker.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.drasyl.handler.connection.ConnectionHandshakeException;

public class CloseOnConnectionHandshakeError extends ChannelInboundHandlerAdapter {
    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx,
                                final Throwable cause) {
        if (cause instanceof ConnectionHandshakeException) {
            ctx.close();
        }
        else {
            ctx.fireExceptionCaught(cause);
        }
    }
}
