package org.drasyl.jtasklet.broker.channel;

import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.jtasklet.broker.TaskletVm;
import org.drasyl.jtasklet.broker.handler.BrokerStartupHandler;
import org.drasyl.jtasklet.broker.handler.TaskletVmsStatusHandler;
import org.drasyl.jtasklet.channel.AbstractChannelInitializer;
import org.drasyl.util.Worm;

import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.util.Map;

import static java.util.Objects.requireNonNull;

public class BrokerChannelInitializer extends AbstractChannelInitializer {
    private final PrintStream out;
    private final Map<IdentityPublicKey, TaskletVm> vms;

    @SuppressWarnings("java:S107")
    public BrokerChannelInitializer(final Identity identity,
                                    final InetSocketAddress bindAddress,
                                    final int networkId,
                                    final long onlineTimeoutMillis,
                                    final Map<IdentityPublicKey, InetSocketAddress> superPeers,
                                    final PrintStream out,
                                    final PrintStream err,
                                    final Worm<Integer> exitCode,
                                    final boolean protocolArmEnabled,
                                    final Map<IdentityPublicKey, TaskletVm> vms) {
        super(identity, bindAddress, networkId, onlineTimeoutMillis, superPeers, protocolArmEnabled, err, exitCode);
        this.out = requireNonNull(out);
        this.vms = requireNonNull(vms);
    }

    @Override
    protected void lastStage(DrasylServerChannel ch) {
        ch.pipeline().addLast(new BrokerStartupHandler(out));
        ch.pipeline().addLast(new TaskletVmsStatusHandler(out, vms));
        super.lastStage(ch);
    }
}
