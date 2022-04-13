package org.drasyl.jtasklet.broker.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.drasyl.handler.discovery.AddPathAndSuperPeerEvent;

import java.io.PrintStream;

import static java.util.Objects.requireNonNull;

public class BrokerStartupHandler extends ChannelInboundHandlerAdapter {
    private final PrintStream out;

    public BrokerStartupHandler(final PrintStream out) {
        this.out = requireNonNull(out);
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        out.print("Start broker...");
        ctx.fireChannelActive();
    }

    @Override
    public void userEventTriggered(final ChannelHandlerContext ctx,
                                   final Object evt) {
        if (evt instanceof AddPathAndSuperPeerEvent) {
            // node is now online
            out.println("online!");
            out.println("----------------------------------------------------------------------------------------------");
            out.println("Broker listening on address " + ctx.channel().localAddress());
            out.println("----------------------------------------------------------------------------------------------");
            ctx.pipeline().remove(this);
        }
        else {
            ctx.fireUserEventTriggered(evt);
        }
    }
}
