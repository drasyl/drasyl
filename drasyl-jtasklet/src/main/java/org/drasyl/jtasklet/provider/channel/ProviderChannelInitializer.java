package org.drasyl.jtasklet.provider.channel;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.handler.PeersRttHandler;
import org.drasyl.handler.PeersRttReport;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.jtasklet.channel.AbstractChannelInitializer;
import org.drasyl.jtasklet.provider.handler.VmStartupHandler;
import org.drasyl.util.Worm;

import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.requireNonNull;

public class ProviderChannelInitializer extends AbstractChannelInitializer {
    private final PrintStream out;
    private final IdentityPublicKey broker;
    private final AtomicReference<PeersRttReport> lastRttReport;
    private final AtomicReference<String> token;

    @SuppressWarnings("java:S107")
    public ProviderChannelInitializer(final Identity identity,
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
        super(identity, bindAddress, networkId, onlineTimeoutMillis, superPeers, protocolArmEnabled, err, exitCode);
        this.out = requireNonNull(out);
        this.broker = broker;
        this.lastRttReport = requireNonNull(lastRttReport);
        this.token = requireNonNull(token);
    }

    @Override
    protected void lastStage(DrasylServerChannel ch) {
        ch.pipeline().addLast(new PeersRttHandler(null, 2_500L));
        ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
            @Override
            public void userEventTriggered(final ChannelHandlerContext ctx,
                                           final Object evt) {
                if (evt instanceof PeersRttReport) {
                    ProviderChannelInitializer.this.lastRttReport.set((PeersRttReport) evt);
                }
                else {
                    ctx.fireUserEventTriggered(evt);
                }
            }
        });
        ch.pipeline().addLast(new VmStartupHandler(out, broker, token));
        super.lastStage(ch);
    }
}
