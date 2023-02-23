package org.drasyl.jtasklet.consumer.channel;

import io.netty.channel.EventLoopGroup;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.cli.channel.AbstractChannelInitializer;
import org.drasyl.handler.PeersRttHandler;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.jtasklet.consumer.handler.ConsumerHandler;

import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;
import static org.drasyl.util.Preconditions.requireNonNegative;
import static org.drasyl.util.Preconditions.requirePositive;

public class ConsumerChannelInitializer extends AbstractChannelInitializer {
    private final PrintStream out;
    private final IdentityPublicKey broker;
    private final String source;
    private final Object[] input;
    private final int cycles;
    private final List<String> tags;
    private final int priority;
    private final List<IdentityPublicKey> peers;

    @SuppressWarnings("java:S107")
    public ConsumerChannelInitializer(final Identity identity,
                                      final EventLoopGroup udpServerGroup,
                                      final InetSocketAddress bindAddress,
                                      final int networkId,
                                      final long onlineTimeoutMillis,
                                      final Map<IdentityPublicKey, InetSocketAddress> superPeers,
                                      final PrintStream out,
                                      final boolean protocolArmEnabled,
                                      final IdentityPublicKey broker,
                                      final String source,
                                      final Object[] input,
                                      final int cycles,
                                      final List<String> tags,
                                      final int priority,
                                      final List<IdentityPublicKey> peers) {
        super(identity, udpServerGroup, bindAddress, networkId, onlineTimeoutMillis, superPeers, protocolArmEnabled);
        this.out = requireNonNull(out);
        this.broker = requireNonNull(broker);
        this.source = requireNonNull(source);
        this.input = requireNonNull(input);
        this.cycles = requirePositive(cycles);
        this.tags = requireNonNull(tags);
        this.priority = requireNonNegative(priority);
        this.peers = requireNonNull(peers);
    }

    @Override
    protected void initChannel(final DrasylServerChannel ch) {
        super.initChannel(ch);

        ch.pipeline().addLast(new ProactiveDirectConnectionHandler(peers));
        ch.pipeline().addLast(new PeersRttHandler(2_500L));
        ch.pipeline().addLast(new ConsumerHandler(out, identity.getAddress(), broker, source, input, cycles, tags, priority));
    }
}
