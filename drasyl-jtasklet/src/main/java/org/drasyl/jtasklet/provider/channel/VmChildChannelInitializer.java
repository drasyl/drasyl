package org.drasyl.jtasklet.provider.channel;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import org.drasyl.channel.DrasylChannel;
import org.drasyl.cli.handler.PrintAndExitOnExceptionHandler;
import org.drasyl.handler.codec.JacksonCodec;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.jtasklet.message.TaskletMessage;
import org.drasyl.jtasklet.provider.handler.ProcessTaskHandler;
import org.drasyl.jtasklet.provider.handler.VmHeartbeatHandler;
import org.drasyl.jtasklet.provider.runtime.RuntimeEnvironment;
import org.drasyl.util.Worm;

import java.io.PrintStream;

import static java.util.Objects.requireNonNull;
import static org.drasyl.node.JSONUtil.JACKSON_MAPPER;

public class VmChildChannelInitializer extends ChannelInitializer<DrasylChannel> {
    private final PrintStream out;
    private final PrintStream err;
    private final Worm<Integer> exitCode;
    private final RuntimeEnvironment runtimeEnvironment;
    private final IdentityPublicKey broker;

    public VmChildChannelInitializer(final PrintStream out,
                                     final PrintStream err,
                                     final Worm<Integer> exitCode,
                                     final RuntimeEnvironment runtimeEnvironment,
                                     final IdentityPublicKey broker) {
        this.out = requireNonNull(out);
        this.err = requireNonNull(err);
        this.exitCode = requireNonNull(exitCode);
        this.runtimeEnvironment = requireNonNull(runtimeEnvironment);
        this.broker = broker;
    }

    @Override
    protected void initChannel(final DrasylChannel ch) {
        final ChannelPipeline p = ch.pipeline();

        p.addLast(new JacksonCodec<>(JACKSON_MAPPER, TaskletMessage.class));

        if (ch.remoteAddress().equals(broker)) {
            p.addLast(new VmHeartbeatHandler());
        }

        p.addLast(new ProcessTaskHandler(runtimeEnvironment));

        p.addLast(new PrintAndExitOnExceptionHandler(err, exitCode));
    }
}
