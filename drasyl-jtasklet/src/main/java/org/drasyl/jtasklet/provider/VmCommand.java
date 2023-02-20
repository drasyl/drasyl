package org.drasyl.jtasklet.provider;

import io.netty.channel.ChannelHandler;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.drasyl.cli.ChannelOptions;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.jtasklet.channel.ChildChannelInitializer;
import org.drasyl.jtasklet.provider.channel.ProviderChannelInitializer;
import org.drasyl.jtasklet.provider.runtime.ExecutionResult;
import org.drasyl.jtasklet.provider.runtime.GraalVmJsRuntimeEnvironment;
import org.drasyl.jtasklet.provider.runtime.RuntimeEnvironment;
import org.drasyl.jtasklet.provider.runtime.ThrottledRuntimeEnvironment;
import org.drasyl.util.EventLoopGroupUtil;
import org.drasyl.util.Worm;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;

@Command(
        name = "vm",
        description = {
                "Starts a Tasklet VM",
                "The VM provides a runtime environment for Tasklets.",
                "If --broker is supplied, the VM will register to given Broker."
        },
        showDefaultValues = true
)
public class VmCommand extends ChannelOptions {
    private static final Logger LOG = LoggerFactory.getLogger(VmCommand.class);
    private static final EventLoopGroup group = EventLoopGroupUtil.getBestEventLoopGroup(1);
    private static final Object[] BENCHMARK_PRIMES_INPUT = new Object[]{ 1, 250_000 };
    private static final Object[] BENCHMARK_EUROPEAN_OPTION_MC_INPUT = new Object[]{ 20_000, 100 };
    private RuntimeEnvironment runtimeEnvironment;
    @Option(
            names = { "--broker" }
    )
    private IdentityPublicKey broker;
    @Option(
            names = { "--benchmark-runs" },
            defaultValue = "5"
    )
    private int benchmarkRuns;
    @Option(
            names = { "--cpu-throttle" },
            defaultValue = "1.0",
            description = {
                    "Simulates a slower CPU.",
                    "Value 1.5 means that CPU will need 50% more time for computation."
            }
    )
    private double cpuThrottle;
    private long benchmark;

    public VmCommand() {
        super(new NioEventLoopGroup(1), new NioEventLoopGroup(1));
    }

    @Override
    public Integer call() {
        setLogLevel();

        runtimeEnvironment = new ThrottledRuntimeEnvironment(new GraalVmJsRuntimeEnvironment(), (float) cpuThrottle);

        try {
            LOG.info("Perform benchmark...");
            benchmark = Long.MAX_VALUE;
            for (int i = 0; i < benchmarkRuns; i++) {
                final ExecutionResult result = runtimeEnvironment.execute(Thread.currentThread().getContextClassLoader().getResourceAsStream("benchmark_primes.js"), BENCHMARK_PRIMES_INPUT);
                if (result.getExecutionTime() < benchmark) {
                    benchmark = result.getExecutionTime();
                }
            }
            LOG.info("Benchmark performed in {}ms.", benchmark);

            return super.call();
        }
        catch (final IOException e) {
            e.printStackTrace();
            return 1;
        }
    }

    @Override
    protected ChannelHandler getHandler(final Worm<Integer> exitCode, final Identity identity) {
        return new ProviderChannelInitializer(identity, group, bindAddress, networkId, onlineTimeoutMillis, superPeers, out, !protocolArmDisabled, broker, benchmark, runtimeEnvironment);
    }

    @Override
    protected ChannelHandler getChildHandler(final Worm<Integer> exitCode,
                                             final Identity identity) {
        return new ChildChannelInitializer(out, true);
    }

    @Override
    protected Logger log() {
        return LOG;
    }
}
