package org.drasyl.jtasklet.broker.channel;

import org.drasyl.channel.DrasylChannel;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.jtasklet.broker.TaskletVm;
import org.drasyl.jtasklet.broker.handler.BrokerReleaseTokenHandler;
import org.drasyl.jtasklet.broker.handler.BrokerResourceRequestHandler;
import org.drasyl.jtasklet.broker.handler.BrokerVmHeartbeatHandler;
import org.drasyl.jtasklet.broker.handler.BrokerVmUpHandler;
import org.drasyl.jtasklet.channel.AbstractChildChannelInitializer;
import org.drasyl.util.Worm;

import java.io.PrintStream;
import java.util.Map;

import static java.util.Objects.requireNonNull;

public class BrokerChildChannelInitializer extends AbstractChildChannelInitializer {
    private final PrintStream err;
    private final Worm<Integer> exitCode;
    private final Map<IdentityPublicKey, TaskletVm> vms;

    public BrokerChildChannelInitializer(final PrintStream out,
                                         final PrintStream err,
                                         final Worm<Integer> exitCode,
                                         final Map<IdentityPublicKey, TaskletVm> vms) {
        super(out, false);
        this.err = requireNonNull(err);
        this.exitCode = requireNonNull(exitCode);
        this.vms = requireNonNull(vms);
    }

    @Override
    protected void lastStage(DrasylChannel ch) {
        ch.pipeline().addLast(
                new BrokerVmHeartbeatHandler(out, vms),
                new BrokerVmUpHandler(out, vms),
                new BrokerResourceRequestHandler(vms),
                new BrokerReleaseTokenHandler(out, vms)
        );

        super.lastStage(ch);
    }
}
