package org.drasyl.jtasklet.handler;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.drasyl.handler.connection.ConnectionHandshakeCompleted;
import org.drasyl.handler.connection.ConnectionHandshakeException;
import org.drasyl.handler.connection.ConnectionHandshakeIssued;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.PrintStream;

import static java.util.Objects.requireNonNull;

public class ConnectionHandshakeStatusHandler extends ChannelDuplexHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ConnectionHandshakeStatusHandler.class);
    private final PrintStream out;
    private final PrintStream err;
    private boolean handshakeFailed;

    public ConnectionHandshakeStatusHandler(final PrintStream out, final PrintStream err) {
        this.out = requireNonNull(out);
        this.err = requireNonNull(err);
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) {
        if (!handshakeFailed) {
            out.println("Close connection to peer " + ctx.channel().remoteAddress() + " ...");

            promise.addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    out.println("Connection to peer " + ctx.channel().remoteAddress() + " closed!");
                }
                else {
                    err.println("Connection to peer " + ctx.channel().remoteAddress() + " closed with error: " + future.cause());
                }
            });
        }
        ctx.close(promise);
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
            err.println("Connection to peer " + ctx.channel().remoteAddress() + " failed: " + cause.getMessage());
            handshakeFailed = true;
        }
        ctx.fireExceptionCaught(cause);
    }
}
