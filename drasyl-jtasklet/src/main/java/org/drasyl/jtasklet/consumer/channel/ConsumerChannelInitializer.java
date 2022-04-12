package org.drasyl.jtasklet.consumer.channel;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.cli.handler.PrintAndExitOnExceptionHandler;
import org.drasyl.cli.handler.SpawnChildChannelToPeer;
import org.drasyl.cli.handler.SuperPeerTimeoutHandler;
import org.drasyl.handler.discovery.AddPathAndSuperPeerEvent;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.jtasklet.channel.AbstractChannelInitializer;
import org.drasyl.jtasklet.handler.PathEventsFilter;
import org.drasyl.util.Worm;

import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.util.Map;

import static java.util.Objects.requireNonNull;

public class ConsumerChannelInitializer extends AbstractChannelInitializer {
    private final PrintStream out;
    private final PrintStream err;
    private final Worm<Integer> exitCode;
    private final IdentityPublicKey broker;

    @SuppressWarnings("java:S107")
    public ConsumerChannelInitializer(final Identity identity,
                                      final InetSocketAddress bindAddress,
                                      final int networkId,
                                      final long onlineTimeoutMillis,
                                      final Map<IdentityPublicKey, InetSocketAddress> superPeers,
                                      final PrintStream out,
                                      final PrintStream err,
                                      final Worm<Integer> exitCode,
                                      final boolean protocolArmEnabled,
                                      final IdentityPublicKey broker) {
        super(identity, bindAddress, networkId, onlineTimeoutMillis, superPeers, protocolArmEnabled);
        this.out = requireNonNull(out);
        this.err = requireNonNull(err);
        this.exitCode = requireNonNull(exitCode);
        this.broker = requireNonNull(broker);
    }

    @Override
    protected void initChannel(final DrasylServerChannel ch) {
        super.initChannel(ch);

        ch.pipeline().addLast(
                new ChannelInboundHandlerAdapter() {
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
                            out.println("Consumer listening on address " + ch.localAddress());
                            out.println("----------------------------------------------------------------------------------------------");
                            ch.pipeline().addFirst(new SpawnChildChannelToPeer(ch, broker));
                            ctx.pipeline().remove(this);
                        }
                        else {
                            ctx.fireUserEventTriggered(evt);
                        }
                    }
                },
                new SuperPeerTimeoutHandler(onlineTimeoutMillis),
                new PathEventsFilter(),
                new PrintAndExitOnExceptionHandler(err, exitCode)
        );
    }
}
