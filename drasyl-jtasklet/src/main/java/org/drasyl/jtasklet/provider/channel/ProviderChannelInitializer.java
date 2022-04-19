package org.drasyl.jtasklet.provider.channel;

import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.handler.PeersRttHandler;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.jtasklet.channel.AbstractChannelInitializer;
import org.drasyl.jtasklet.provider.handler.ProviderHandler;
import org.drasyl.jtasklet.provider.runtime.RuntimeEnvironment;
import org.drasyl.util.Worm;

import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.util.Map;

import static java.util.Objects.requireNonNull;

public class ProviderChannelInitializer extends AbstractChannelInitializer {
    private final PrintStream out;
    private final IdentityPublicKey broker;
    private final long benchmark;
    private final RuntimeEnvironment runtimeEnvironment;

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
                                      final long benchmark,
                                      RuntimeEnvironment runtimeEnvironment) {
        super(identity, bindAddress, networkId, onlineTimeoutMillis, superPeers, protocolArmEnabled, err, exitCode);
        this.out = requireNonNull(out);
        this.broker = broker;
        this.benchmark = benchmark;
        this.runtimeEnvironment = requireNonNull(runtimeEnvironment);
    }

    @Override
    protected void lastStage(DrasylServerChannel ch) {
        ch.pipeline().addLast(new PeersRttHandler(null, 2_500L));
        ch.pipeline().addLast(new ProviderHandler(out, err, broker, benchmark, runtimeEnvironment));
        super.lastStage(ch);
    }
}
