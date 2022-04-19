package org.drasyl.jtasklet.broker.channel;

import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.jtasklet.broker.handler.BrokerHandler;
import org.drasyl.jtasklet.channel.AbstractChannelInitializer;
import org.drasyl.util.Worm;

import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.util.Map;

import static java.util.Objects.requireNonNull;

public class BrokerChannelInitializer extends AbstractChannelInitializer {
    private final PrintStream out;

    @SuppressWarnings("java:S107")
    public BrokerChannelInitializer(final Identity identity,
                                    final InetSocketAddress bindAddress,
                                    final int networkId,
                                    final long onlineTimeoutMillis,
                                    final Map<IdentityPublicKey, InetSocketAddress> superPeers,
                                    final PrintStream out,
                                    final PrintStream err,
                                    final Worm<Integer> exitCode,
                                    final boolean protocolArmEnabled) {
        super(identity, bindAddress, networkId, onlineTimeoutMillis, superPeers, protocolArmEnabled, err, exitCode);
        this.out = requireNonNull(out);
    }

    @Override
    protected void lastStage(DrasylServerChannel ch) throws Exception {
        ch.pipeline().addLast(new BrokerHandler(out, err));
        super.lastStage(ch);
    }
}
