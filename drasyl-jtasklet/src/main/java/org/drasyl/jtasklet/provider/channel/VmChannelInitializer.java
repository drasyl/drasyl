package org.drasyl.jtasklet.provider.channel;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.cli.handler.PrintAndExitOnExceptionHandler;
import org.drasyl.cli.handler.SpawnChildChannelToPeer;
import org.drasyl.handler.discovery.AddPathAndSuperPeerEvent;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.jtasklet.channel.AbstractChannelInitializer;
import org.drasyl.jtasklet.handler.PathEventsFilter;
import org.drasyl.util.Worm;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.util.Map;

import static java.util.Objects.requireNonNull;

public class VmChannelInitializer extends AbstractChannelInitializer {
    private static final Logger LOG = LoggerFactory.getLogger(VmChannelInitializer.class);
    private final PrintStream out;
    private final PrintStream err;
    private final Worm<Integer> exitCode;
    private final IdentityPublicKey broker;

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
                                final IdentityPublicKey broker) {
        super(identity, bindAddress, networkId, onlineTimeoutMillis, superPeers, protocolArmEnabled);
        this.out = requireNonNull(out);
        this.err = requireNonNull(err);
        this.exitCode = requireNonNull(exitCode);
        this.broker = broker;
    }

    @Override
    protected void initChannel(final DrasylServerChannel ch) {
        super.initChannel(ch);

        final ChannelPipeline p = ch.pipeline();

        p.addLast(new ChannelInboundHandlerAdapter() {
            @Override
            public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) {
                if (evt instanceof AddPathAndSuperPeerEvent) {
                    // node is now online
                    out.println("----------------------------------------------------------------------------------------------");
                    out.println("VM listening on address " + ch.localAddress());
                    out.println("----------------------------------------------------------------------------------------------");

                    if (broker != null) {
                        LOG.info("This VM will register at broker `{}`", broker);
                        ctx.pipeline().addFirst(new SpawnChildChannelToPeer(ch, broker));
                    }

                    ctx.pipeline().remove(this);
                }
                else {
                    ctx.fireUserEventTriggered(evt);
                }
            }
        });
        p.addLast(new PathEventsFilter());
        p.addLast(new PrintAndExitOnExceptionHandler(err, exitCode));
    }
}
