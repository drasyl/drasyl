package org.drasyl.jtasklet.provider;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import org.drasyl.cli.ChannelOptions;
import org.drasyl.handler.PeersRttReport;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.jtasklet.provider.channel.ProviderChannelInitializer;
import org.drasyl.jtasklet.provider.channel.ProviderChildChannelInitializer;
import org.drasyl.jtasklet.provider.runtime.ExecutionResult;
import org.drasyl.jtasklet.provider.runtime.GraalVmJsRuntimeEnvironment;
import org.drasyl.jtasklet.provider.runtime.RuntimeEnvironment;
import org.drasyl.util.Worm;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

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
    private static final Object[] BENCHMARK_PRIMES_INPUT = new Object[]{ 1, 250 };
    private static final Object[] BENCHMARK_EUROPEAN_OPTION_MC_INPUT = new Object[]{ 20_000, 100 };
    private final RuntimeEnvironment runtimeEnvironment;
    @Option(
            names = { "--broker" }
    )
    private IdentityPublicKey broker;
    private final AtomicReference<PeersRttReport> lastRttReport = new AtomicReference<>();
    private long benchmark;
    private final AtomicReference<Channel> brokerChannel = new AtomicReference<>();
    private final AtomicReference<String> token = new AtomicReference<>();

    public VmCommand() {
        super(new NioEventLoopGroup(1), new NioEventLoopGroup(1));
        runtimeEnvironment = new GraalVmJsRuntimeEnvironment();
    }

    @Override
    public Integer call() {
        try {
            out.println("Perform benchmark...");
            benchmark = Long.MAX_VALUE;
            for (int i = 0; i < 5; i++) {
                final ExecutionResult result = runtimeEnvironment.execute(Thread.currentThread().getContextClassLoader().getResourceAsStream("benchmark_primes.js"), BENCHMARK_PRIMES_INPUT);
                if (result.getExecutionTime() < benchmark) {
                    benchmark = result.getExecutionTime();
                }
            }
            out.println("Benchmark: " + benchmark + "ms");

            return super.call();
        }
        catch (final IOException e) {
            e.printStackTrace();
            return 1;
        }
    }

    @Override
    protected ChannelHandler getHandler(final Worm<Integer> exitCode, final Identity identity) {
        return new ProviderChannelInitializer(identity, bindAddress, networkId, onlineTimeoutMillis, superPeers, out, err, exitCode, !protocolArmDisabled, broker, lastRttReport, token);
    }

    @Override
    protected ChannelHandler getChildHandler(final Worm<Integer> exitCode,
                                             final Identity identity) {
        return new ProviderChildChannelInitializer(out, err, exitCode, runtimeEnvironment, broker, lastRttReport, benchmark, brokerChannel, token);
    }

    @Override
    protected Logger log() {
        return LOG;
    }
}
