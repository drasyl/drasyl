package org.drasyl.jtasklet.provider.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.cli.handler.SpawnChildChannelToPeer;
import org.drasyl.handler.discovery.AddPathAndSuperPeerEvent;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.RandomUtil;

import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.requireNonNull;

public class VmStartupHandler extends ChannelInboundHandlerAdapter {
    private final PrintStream out;
    private final IdentityPublicKey broker;
    private final AtomicReference<String> token;

    public VmStartupHandler(final PrintStream out,
                            final IdentityPublicKey broker,
                            final AtomicReference<String> token) {
        this.out = requireNonNull(out);
        this.broker = requireNonNull(broker);
        this.token = requireNonNull(token);
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        out.print("Start VM...");
        ctx.fireChannelActive();
    }

    @Override
    public void userEventTriggered(final ChannelHandlerContext ctx,
                                   final Object evt) {
        if (evt instanceof AddPathAndSuperPeerEvent) {
            // node is now online
            out.println("online!");
            out.println("----------------------------------------------------------------------------------------------");
            out.println("VM listening on address " + ctx.channel().localAddress());
            if (broker != null) {
                out.println("This VM will register at broker " + broker);
            }
            out.println("----------------------------------------------------------------------------------------------");
            token.set(RandomUtil.randomString(6));
            if (broker != null) {
                ctx.pipeline().addFirst(new SpawnChildChannelToPeer((DrasylServerChannel) ctx.channel(), broker));
            }
            ctx.pipeline().remove(this);
            out.println("Send me tasks! I'm hungry!");
        }
        else {
            ctx.fireUserEventTriggered(evt);
        }
    }
}
