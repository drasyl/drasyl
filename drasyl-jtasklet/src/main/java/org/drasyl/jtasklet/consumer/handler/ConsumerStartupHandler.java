package org.drasyl.jtasklet.consumer.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.cli.handler.SpawnChildChannelToPeer;
import org.drasyl.handler.discovery.AddPathAndSuperPeerEvent;
import org.drasyl.identity.IdentityPublicKey;

import java.io.PrintStream;

import static java.util.Objects.requireNonNull;

public class ConsumerStartupHandler extends ChannelInboundHandlerAdapter {
    private final PrintStream out;
    private final IdentityPublicKey broker;

    public ConsumerStartupHandler(final PrintStream out,
                                  IdentityPublicKey broker) {
        this.out = requireNonNull(out);
        this.broker = requireNonNull(broker);
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        out.print("Start consumer...");
        ctx.fireChannelActive();
    }

    @Override
    public void userEventTriggered(final ChannelHandlerContext ctx,
                                   final Object evt) {
        if (evt instanceof AddPathAndSuperPeerEvent) {
            // node is now online
            out.println("online!");
            out.println("----------------------------------------------------------------------------------------------");
            out.println("Consumer listening on address " + ctx.channel().localAddress());
            out.println("This Consumer will contact broker " + broker);
            out.println("----------------------------------------------------------------------------------------------");
            ctx.channel().pipeline().addFirst(new SpawnChildChannelToPeer((DrasylServerChannel) ctx.channel(), broker));
            ctx.pipeline().remove(this);
        }
        else {
            ctx.fireUserEventTriggered(evt);
        }
    }
}
