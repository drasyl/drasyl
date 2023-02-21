package org.drasyl.jtasklet.broker;

import io.netty.channel.ChannelHandler;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.drasyl.cli.ChannelOptions;
import org.drasyl.identity.Identity;
import org.drasyl.jtasklet.broker.channel.BrokerChannelInitializer;
import org.drasyl.jtasklet.broker.scheduler.BenchmarkSchedulingStrategy;
import org.drasyl.jtasklet.broker.scheduler.RandomSchedulingStrategy;
import org.drasyl.jtasklet.broker.scheduler.RttSchedulingStrategy;
import org.drasyl.jtasklet.broker.scheduler.SchedulingStrategy;
import org.drasyl.jtasklet.broker.scheduler.experiment.S1;
import org.drasyl.jtasklet.broker.scheduler.experiment.S2;
import org.drasyl.jtasklet.broker.scheduler.experiment.S3;
import org.drasyl.jtasklet.broker.scheduler.experiment.S4;
import org.drasyl.jtasklet.channel.ChildChannelInitializer;
import org.drasyl.util.EventLoopGroupUtil;
import org.drasyl.util.Worm;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import static java.util.Objects.requireNonNull;

@Command(
        name = "broker",
        description = {
                "Starts a broker",
                "The broker performs the resource matchmaking.",
                "For this Tasklet VMs need to register to this broker.",
                "If a Resource Consumer requests a resource, the broker will select a suitable VM."
        },
        showDefaultValues = true
)
public class BrokerCommand extends ChannelOptions {
    private static final Logger LOG = LoggerFactory.getLogger(BrokerCommand.class);
    private static final EventLoopGroup group = EventLoopGroupUtil.getBestEventLoopGroup(1);
    @Option(
            names = { "--scheduler" },
            defaultValue = "random"
    )
    protected SchedulingStrategyType schedulingStrategyType;

    public BrokerCommand() {
        super(new NioEventLoopGroup(1), new NioEventLoopGroup());
    }

    @Override
    protected ChannelHandler getHandler(final Worm<Integer> exitCode, final Identity identity) {
        return new BrokerChannelInitializer(identity, group, bindAddress, networkId, onlineTimeoutMillis, superPeers, out, !protocolArmDisabled, schedulingStrategyType.schedulingStrategy);
    }

    @Override
    protected ChannelHandler getChildHandler(final Worm<Integer> exitCode,
                                             final Identity identity) {
        return new ChildChannelInitializer(out, false);
    }

    @Override
    protected Logger log() {
        return LOG;
    }

    enum SchedulingStrategyType {
        random(new RandomSchedulingStrategy()),
        benchmark(new BenchmarkSchedulingStrategy()),
        rtt(new RttSchedulingStrategy()),
        s1(new S1()),
        s2(new S2()),
        s3(new S3()),
        s4(new S4());
        private final SchedulingStrategy schedulingStrategy;

        SchedulingStrategyType(final SchedulingStrategy schedulingStrategy) {
            this.schedulingStrategy = requireNonNull(schedulingStrategy);
        }
    }
}
