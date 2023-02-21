package org.drasyl.jtasklet.provider.channel;

import io.netty.channel.EventLoopGroup;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.cli.channel.AbstractChannelInitializer;
import org.drasyl.handler.PeersRttHandler;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.jtasklet.provider.handler.ProviderHandler;
import org.drasyl.jtasklet.provider.runtime.RuntimeEnvironment;

import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.util.Map;

import static java.util.Objects.requireNonNull;

public class ProviderChannelInitializer extends AbstractChannelInitializer {
    private final PrintStream out;
    private final IdentityPublicKey broker;
    private final long benchmark;
    private final RuntimeEnvironment runtimeEnvironment;
    private final String[] tags;

    @SuppressWarnings("java:S107")
    public ProviderChannelInitializer(final Identity identity,
                                      final EventLoopGroup udpServerGroup,
                                      final InetSocketAddress bindAddress,
                                      final int networkId,
                                      final long onlineTimeoutMillis,
                                      final Map<IdentityPublicKey, InetSocketAddress> superPeers,
                                      final PrintStream out,
                                      final boolean protocolArmEnabled,
                                      final IdentityPublicKey broker,
                                      final long benchmark,
                                      final RuntimeEnvironment runtimeEnvironment,
                                      final String[] tags) {
        super(identity, udpServerGroup, bindAddress, networkId, onlineTimeoutMillis, superPeers, protocolArmEnabled);
        this.out = requireNonNull(out);
        this.broker = broker;
        this.benchmark = benchmark;
        this.runtimeEnvironment = requireNonNull(runtimeEnvironment);
        this.tags = tags;
    }

    @Override
    protected void initChannel(final DrasylServerChannel ch) {
        super.initChannel(ch);
        ch.pipeline().addLast(new PeersRttHandler(2_500L));
        ch.pipeline().addLast(new ProviderHandler(out, identity.getAddress(), broker, benchmark, runtimeEnvironment, tags));
    }
}
