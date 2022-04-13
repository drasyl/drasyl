package org.drasyl.jtasklet.provider;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import org.drasyl.cli.ChannelOptions;
import org.drasyl.handler.PeersRttReport;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.jtasklet.provider.channel.VmChannelInitializer;
import org.drasyl.jtasklet.provider.channel.VmChildChannelInitializer;
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
    private static final Object[] BENCHMARK_INPUT = new Object[]{ 1, 25_000 };
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
            final ExecutionResult result = runtimeEnvironment.execute(Thread.currentThread().getContextClassLoader().getResourceAsStream("benchmark.js"), BENCHMARK_INPUT);
            benchmark = result.getExecutionTime();
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
        return new VmChannelInitializer(identity, bindAddress, networkId, onlineTimeoutMillis, superPeers, out, err, exitCode, !protocolArmDisabled, broker, lastRttReport, token);
    }

    @Override
    protected ChannelHandler getChildHandler(final Worm<Integer> exitCode,
                                             final Identity identity) {
        return new VmChildChannelInitializer(out, err, exitCode, runtimeEnvironment, broker, lastRttReport, benchmark, brokerChannel, token);
    }

    @Override
    protected Logger log() {
        return LOG;
    }
}
