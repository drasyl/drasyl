package org.drasyl.jtasklet.broker.channel;

import io.netty.channel.EventLoopGroup;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.cli.channel.AbstractChannelInitializer;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.jtasklet.broker.handler.BrokerHandler;
import org.drasyl.jtasklet.broker.scheduler.SchedulingStrategy;

import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.util.Map;

import static java.util.Objects.requireNonNull;

public class BrokerChannelInitializer extends AbstractChannelInitializer {
    private final PrintStream out;
    private final SchedulingStrategy schedulingStrategy;

    @SuppressWarnings("java:S107")
    public BrokerChannelInitializer(final Identity identity,
                                    final EventLoopGroup udpServerGroup,
                                    final InetSocketAddress bindAddress,
                                    final int networkId,
                                    final long onlineTimeoutMillis,
                                    final Map<IdentityPublicKey, InetSocketAddress> superPeers,
                                    final PrintStream out,
                                    final boolean protocolArmEnabled,
                                    final SchedulingStrategy schedulingStrategy) {
        super(identity, udpServerGroup, bindAddress, networkId, onlineTimeoutMillis, superPeers, protocolArmEnabled);
        this.out = requireNonNull(out);
        this.schedulingStrategy = requireNonNull(schedulingStrategy);
    }

    @Override
    protected void initChannel(final DrasylServerChannel ch) {
        super.initChannel(ch);
        ch.pipeline().addLast(new BrokerHandler(out, identity.getAddress(), schedulingStrategy));
    }
}
