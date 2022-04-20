package org.drasyl.jtasklet.provider;

import io.netty.channel.ChannelHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import org.drasyl.cli.ChannelOptions;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.jtasklet.channel.ChildChannelInitializer;
import org.drasyl.jtasklet.provider.channel.ProviderChannelInitializer;
import org.drasyl.jtasklet.provider.runtime.ExecutionResult;
import org.drasyl.jtasklet.provider.runtime.GraalVmJsRuntimeEnvironment;
import org.drasyl.jtasklet.provider.runtime.RuntimeEnvironment;
import org.drasyl.util.Worm;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;

@Command(
        name = "vm",
        description = {
                "Starts a Resource Provider",
                "The Provider is capable to run one Task per time."
        },
        showDefaultValues = true
)
public class ProviderCommand extends ChannelOptions {
    private static final Logger LOG = LoggerFactory.getLogger(ProviderCommand.class);
    private static final Object[] BENCHMARK_PRIMES_INPUT = new Object[]{ 1, 250_000 };
    private static final Object[] BENCHMARK_EUROPEAN_OPTION_MC_INPUT = new Object[]{ 20_000, 100 };
    private final RuntimeEnvironment runtimeEnvironment;
    @Option(
            names = { "--broker" }
    )
    private IdentityPublicKey broker;
    @Option(
            names = { "--benchmark-runs" },
            defaultValue = "5"
    )
    private int benchmarkRuns;
    private long benchmark;

    public ProviderCommand() {
        super(new NioEventLoopGroup(1), new NioEventLoopGroup(1));
        runtimeEnvironment = new GraalVmJsRuntimeEnvironment();
    }

    @Override
    public Integer call() {
        setLogLevel();

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
        return new ProviderChannelInitializer(identity, bindAddress, networkId, onlineTimeoutMillis, superPeers, out, err, exitCode, !protocolArmDisabled, broker, benchmark, runtimeEnvironment);
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
