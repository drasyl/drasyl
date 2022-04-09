package org.drasyl.jtasklet.broker.channel;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import org.drasyl.channel.DrasylChannel;
import org.drasyl.cli.handler.PrintAndExitOnExceptionHandler;
import org.drasyl.handler.codec.JacksonCodec;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.jtasklet.broker.BrokerCommand.TaskletVm;
import org.drasyl.jtasklet.broker.handler.BrokerResourceRequestHandler;
import org.drasyl.jtasklet.broker.handler.BrokerVmHeartbeatHandler;
import org.drasyl.jtasklet.message.TaskletMessage;
import org.drasyl.util.Worm;

import java.io.PrintStream;
import java.util.Map;

import static java.util.Objects.requireNonNull;
import static org.drasyl.node.JSONUtil.JACKSON_MAPPER;

public class BrokerChildChannelInitializer extends ChannelInitializer<DrasylChannel> {
    private final PrintStream out;
    private final PrintStream err;
    private final Worm<Integer> exitCode;
    private final Map<IdentityPublicKey, TaskletVm> vms;

    public BrokerChildChannelInitializer(final PrintStream out,
                                         final PrintStream err,
                                         final Worm<Integer> exitCode,
                                         final Map<IdentityPublicKey, TaskletVm> vms) {
        this.out = requireNonNull(out);
        this.err = requireNonNull(err);
        this.exitCode = requireNonNull(exitCode);
        this.vms = requireNonNull(vms);
    }

    @Override
    protected void initChannel(final DrasylChannel ch) {
        final ChannelPipeline p = ch.pipeline();

        p.addLast(new JacksonCodec<>(JACKSON_MAPPER, TaskletMessage.class));

        p.addLast(new BrokerVmHeartbeatHandler(vms));
        p.addLast(new BrokerResourceRequestHandler(vms));

        p.addLast(new PrintAndExitOnExceptionHandler(err, exitCode));
    }
}
