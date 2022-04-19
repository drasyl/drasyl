package org.drasyl.jtasklet.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.WriteTimeoutException;
import org.drasyl.channel.DrasylChannel;
import org.drasyl.handler.connection.ConnectionHandshakeException;
import org.drasyl.jtasklet.event.ConnectionClosed;
import org.drasyl.jtasklet.event.ConnectionFailed;
import org.drasyl.jtasklet.event.ConnectionLost;

public class ConnectionEventHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        ctx.channel().parent().pipeline().fireUserEventTriggered(new ConnectionClosed((DrasylChannel) ctx.channel()));
        ctx.fireChannelInactive();
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx,
                                final Throwable cause) {
        if (cause instanceof ConnectionHandshakeException) {
            ctx.channel().parent().pipeline().fireUserEventTriggered(new ConnectionFailed((DrasylChannel) ctx.channel(), cause));
            ctx.pipeline().close();
        }
        else if (cause instanceof WriteTimeoutException) {
            ctx.channel().parent().pipeline().fireUserEventTriggered(new ConnectionLost((DrasylChannel) ctx.channel()));
            ctx.pipeline().close();
        }
        else {
            ctx.fireExceptionCaught(cause);
        }
    }
}
