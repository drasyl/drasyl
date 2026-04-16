package org.drasyl.jtasklet.consumer.channel;

import io.netty.channel.EventLoopGroup;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.cli.channel.AbstractChannelInitializer;
import org.drasyl.handler.PeersRttHandler;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.jtasklet.consumer.handler.VNMIFEConsumerHandler;
import org.drasyl.jtasklet.vnmife.VNMIFETaskPayload;

import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;
import static org.drasyl.util.Preconditions.requireNonNegative;
import static org.drasyl.util.Preconditions.requirePositive;

public class VNMIFEConsumerChannelInitializer extends AbstractChannelInitializer {
    private final PrintStream out;
    private final IdentityPublicKey broker;
    private final String source;
    private final Object[] input;
    private final VNMIFETaskPayload payload;
    private final Path clientKeyDir;
    private final int cycles;
    private final List<String> tags;
    private final int priority;
    private final List<IdentityPublicKey> peers;

    @SuppressWarnings("java:S107")
    public VNMIFEConsumerChannelInitializer(final Identity identity,
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
                                            final VNMIFETaskPayload payload,
                                            final Path clientKeyDir,
                                            final int cycles,
                                            final List<String> tags,
                                            final int priority,
                                            final List<IdentityPublicKey> peers) {
        super(identity, udpServerGroup, bindAddress, networkId, onlineTimeoutMillis, superPeers, protocolArmEnabled);
        this.out = requireNonNull(out);
        this.broker = requireNonNull(broker);
        this.source = requireNonNull(source);
        this.input = requireNonNull(input);
        this.payload = requireNonNull(payload);
        this.clientKeyDir = requireNonNull(clientKeyDir);
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
        ch.pipeline().addLast(new VNMIFEConsumerHandler(out, identity.getAddress(), broker, source, input, payload, clientKeyDir, cycles, tags, priority));
    }
}
