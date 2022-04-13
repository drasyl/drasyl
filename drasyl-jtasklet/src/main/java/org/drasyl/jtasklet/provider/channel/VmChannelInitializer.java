package org.drasyl.jtasklet.provider.channel;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.cli.handler.PrintAndExitOnExceptionHandler;
import org.drasyl.cli.handler.SpawnChildChannelToPeer;
import org.drasyl.cli.handler.SuperPeerTimeoutHandler;
import org.drasyl.handler.PeersRttHandler;
import org.drasyl.handler.PeersRttReport;
import org.drasyl.handler.discovery.AddPathAndSuperPeerEvent;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.jtasklet.channel.AbstractChannelInitializer;
import org.drasyl.jtasklet.handler.PathEventsFilter;
import org.drasyl.util.RandomUtil;
import org.drasyl.util.Worm;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.requireNonNull;

public class VmChannelInitializer extends AbstractChannelInitializer {
    private static final Logger LOG = LoggerFactory.getLogger(VmChannelInitializer.class);
    private final PrintStream out;
    private final PrintStream err;
    private final Worm<Integer> exitCode;
    private final IdentityPublicKey broker;
    private final AtomicReference<PeersRttReport> lastRttReport;
    private final AtomicReference<String> token;

    @SuppressWarnings("java:S107")
    public VmChannelInitializer(final Identity identity,
                                final InetSocketAddress bindAddress,
                                final int networkId,
                                final long onlineTimeoutMillis,
                                final Map<IdentityPublicKey, InetSocketAddress> superPeers,
                                final PrintStream out,
                                final PrintStream err,
                                final Worm<Integer> exitCode,
                                final boolean protocolArmEnabled,
                                final IdentityPublicKey broker,
                                final AtomicReference<PeersRttReport> lastRttReport,
                                final AtomicReference<String> token) {
        super(identity, bindAddress, networkId, onlineTimeoutMillis, superPeers, protocolArmEnabled);
        this.out = requireNonNull(out);
        this.err = requireNonNull(err);
        this.exitCode = requireNonNull(exitCode);
        this.broker = broker;
        this.lastRttReport = requireNonNull(lastRttReport);
        this.token = requireNonNull(token);
    }

    @Override
    protected void initChannel(final DrasylServerChannel ch) {
        super.initChannel(ch);

        ch.pipeline().addLast(
                new PeersRttHandler(null, 2_500L),
                new ChannelInboundHandlerAdapter() {
                    @Override
                    public void userEventTriggered(final ChannelHandlerContext ctx,
                                                   final Object evt) {
                        if (evt instanceof PeersRttReport) {
                            VmChannelInitializer.this.lastRttReport.set((PeersRttReport) evt);
                        }
                        else {
                            ctx.fireUserEventTriggered(evt);
                        }
                    }
                },
                new ChannelInboundHandlerAdapter() {
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
                            out.println("VM listening on address " + ch.localAddress());
                            if (broker != null) {
                                out.println("This VM will register at broker " + broker);
                            }
                            out.println("----------------------------------------------------------------------------------------------");
                            token.set(RandomUtil.randomString(6));
                            if (broker != null) {
                                ctx.pipeline().addFirst(new SpawnChildChannelToPeer(ch, broker));
                            }
                            ctx.pipeline().remove(this);
                            out.println("Send me tasks! I'm hungry!");
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
