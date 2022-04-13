package org.drasyl.jtasklet.broker.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.drasyl.handler.connection.ConnectionHandshakeCompleted;
import org.drasyl.handler.connection.ConnectionHandshakeException;
import org.drasyl.handler.connection.ConnectionHandshakeIssued;

import java.io.PrintStream;

import static java.util.Objects.requireNonNull;

public class JTaskletConnectionHandshakeHandler extends ChannelInboundHandlerAdapter {
    private final PrintStream out;

    public JTaskletConnectionHandshakeHandler(final PrintStream out) {
        this.out = requireNonNull(out);
    }

    @Override
    public void userEventTriggered(final ChannelHandlerContext ctx,
                                   final Object evt) {
        if (evt instanceof ConnectionHandshakeIssued) {
            out.println("Connect to peer " + ctx.channel().remoteAddress() + " ...");
        }
        else if (evt instanceof ConnectionHandshakeCompleted) {
            out.println("Connection to peer " + ctx.channel().remoteAddress() + " established!");
        }

        ctx.fireUserEventTriggered(evt);
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx,
                                final Throwable cause) {
        if (cause instanceof ConnectionHandshakeException) {
            out.println("Connection to peer " + ctx.channel().remoteAddress() + " failed: " + cause.getMessage());
            ctx.close();
        }
        else {
            ctx.fireExceptionCaught(cause);
        }
    }
}
