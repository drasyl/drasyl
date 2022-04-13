package org.drasyl.jtasklet.consumer.channel;

import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.jtasklet.channel.AbstractChannelInitializer;
import org.drasyl.jtasklet.consumer.handler.ConsumerStartupHandler;
import org.drasyl.util.Worm;

import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.util.Map;

import static java.util.Objects.requireNonNull;

public class ConsumerChannelInitializer extends AbstractChannelInitializer {
    private final PrintStream out;
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
        super(identity, bindAddress, networkId, onlineTimeoutMillis, superPeers, protocolArmEnabled, err, exitCode);
        this.out = requireNonNull(out);
        this.broker = requireNonNull(broker);
    }

    @Override
    protected void lastStage(DrasylServerChannel ch) {
        ch.pipeline().addLast(new ConsumerStartupHandler(out, broker));
        super.lastStage(ch);
    }
}
