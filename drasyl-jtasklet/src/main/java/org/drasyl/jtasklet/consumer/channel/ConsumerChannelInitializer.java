package org.drasyl.jtasklet.consumer.channel;

import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.handler.PeersRttHandler;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.jtasklet.channel.AbstractChannelInitializer;
import org.drasyl.jtasklet.consumer.handler.ConsumerHandler;
import org.drasyl.util.Worm;

import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.util.Map;

import static java.util.Objects.requireNonNull;
import static org.drasyl.util.Preconditions.requirePositive;

public class ConsumerChannelInitializer extends AbstractChannelInitializer {
    private final PrintStream out;
    private final IdentityPublicKey broker;
    private final String source;
    private final Object[] input;
    private final int cycles;

    @SuppressWarnings("java:S107")
    public ConsumerChannelInitializer(final Identity identity,
                                      final InetSocketAddress bindAddress,
                                      final int networkId,
                                      final long onlineTimeoutMillis,
                                      final Map<IdentityPublicKey, InetSocketAddress> superPeers,
                                      final Map<IdentityPublicKey, InetSocketAddress> staticRoutes,
                                      final PrintStream out,
                                      final PrintStream err,
                                      final Worm<Integer> exitCode,
                                      final boolean protocolArmEnabled,
                                      final IdentityPublicKey broker,
                                      final String source,
                                      final Object[] input,
                                      final int cycles) {
        super(identity, bindAddress, networkId, onlineTimeoutMillis, superPeers, staticRoutes, protocolArmEnabled, err, exitCode);
        this.out = requireNonNull(out);
        this.broker = requireNonNull(broker);
        this.source = requireNonNull(source);
        this.input = requireNonNull(input);
        this.cycles = requirePositive(cycles);
    }

    @Override
    protected void lastStage(final DrasylServerChannel ch) throws Exception {
        ch.pipeline().addLast(new PeersRttHandler(null, 2_500L));
        ch.pipeline().addLast(new ConsumerHandler(out, err, identity.getAddress(), broker, source, input, cycles));
        super.lastStage(ch);
    }
}
