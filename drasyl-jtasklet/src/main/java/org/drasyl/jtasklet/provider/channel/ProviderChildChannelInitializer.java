package org.drasyl.jtasklet.provider.channel;

import io.netty.channel.Channel;
import org.drasyl.channel.DrasylChannel;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.cli.handler.SpawnChildChannelToPeer;
import org.drasyl.handler.PeersRttReport;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.jtasklet.channel.AbstractChildChannelInitializer;
import org.drasyl.jtasklet.provider.handler.ProcessTaskHandler;
import org.drasyl.jtasklet.provider.handler.VmHeartbeatHandler;
import org.drasyl.jtasklet.provider.runtime.RuntimeEnvironment;
import org.drasyl.util.Worm;

import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.requireNonNull;
import static org.drasyl.util.Preconditions.requirePositive;

public class ProviderChildChannelInitializer extends AbstractChildChannelInitializer {
    private final PrintStream err;
    private final Worm<Integer> exitCode;
    private final RuntimeEnvironment runtimeEnvironment;
    private final IdentityPublicKey broker;
    private final AtomicReference<PeersRttReport> lastRttReport;
    private final long benchmark;
    private final AtomicReference<Channel> brokerChannel;
    private final AtomicReference<String> token;

    @SuppressWarnings("java:S107")
    public ProviderChildChannelInitializer(final PrintStream out,
                                           final PrintStream err,
                                           final Worm<Integer> exitCode,
                                           final RuntimeEnvironment runtimeEnvironment,
                                           final IdentityPublicKey broker,
                                           final AtomicReference<PeersRttReport> lastRttReport,
                                           final long benchmark,
                                           final AtomicReference<Channel> brokerChannel,
                                           final AtomicReference<String> token) {
        super(out);
        this.err = requireNonNull(err);
        this.exitCode = requireNonNull(exitCode);
        this.runtimeEnvironment = requireNonNull(runtimeEnvironment);
        this.broker = broker;
        this.lastRttReport = requireNonNull(lastRttReport);
        this.benchmark = requirePositive(benchmark);
        this.brokerChannel = requireNonNull(brokerChannel);
        this.token = requireNonNull(token);
    }

    @Override
    protected void lastStage(DrasylChannel ch) {
        final boolean isBroker = ch.remoteAddress().equals(broker);

        if (isBroker) {
            brokerStage(ch);
        }
        else {
            consumerStage(ch);
        }

        super.lastStage(ch);
    }

    private void brokerStage(final DrasylChannel ch) {
        brokerChannel.set(ch);

        ch.pipeline().addLast(new VmHeartbeatHandler(lastRttReport, benchmark, err, token));

        // always create a new channel to the broker
        ch.closeFuture().addListener(future -> ch.parent().pipeline().addFirst(new SpawnChildChannelToPeer((DrasylServerChannel) ch.parent(), (IdentityPublicKey) ch.remoteAddress())));
    }

    private void consumerStage(final DrasylChannel ch) {
        ch.pipeline().addLast(new ProcessTaskHandler(runtimeEnvironment, out, brokerChannel, token));
    }
}
