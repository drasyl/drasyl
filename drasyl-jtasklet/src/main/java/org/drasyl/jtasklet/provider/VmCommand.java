package org.drasyl.jtasklet.provider;

import io.netty.channel.ChannelHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import org.drasyl.cli.ChannelOptions;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.jtasklet.provider.channel.VmChannelInitializer;
import org.drasyl.jtasklet.provider.channel.VmChildChannelInitializer;
import org.drasyl.jtasklet.provider.runtime.GraalVmJsRuntimeEnvironment;
import org.drasyl.jtasklet.provider.runtime.RuntimeEnvironment;
import org.drasyl.util.Worm;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.Paths;

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
    private final RuntimeEnvironment runtimeEnvironment;
    @Option(
            names = { "--broker" }
    )
    private IdentityPublicKey broker;
    private long benchmarkTime;

    public VmCommand() {
        super(new NioEventLoopGroup(1));
        runtimeEnvironment = new GraalVmJsRuntimeEnvironment();
    }

    @Override
    public Integer call() {
        try {
            final long startTime = System.currentTimeMillis();
            runtimeEnvironment.execute(Paths.get("tasks", "prime.js"), 1, 1000);
            final long endTime = System.currentTimeMillis();
            benchmarkTime = endTime - startTime;
            LOG.info("Benchmark Time: {}ms", benchmarkTime);

            return super.call();
        }
        catch (final IOException e) {
            e.printStackTrace();
            return 1;
        }
    }

    @Override
    protected ChannelHandler getHandler(final Worm<Integer> exitCode, final Identity identity) {
        return new VmChannelInitializer(identity, bindAddress, networkId, onlineTimeoutMillis, superPeers, out, err, exitCode, !protocolArmDisabled, broker);
    }

    @Override
    protected ChannelHandler getChildHandler(final Worm<Integer> exitCode,
                                             final Identity identity) {
        return new VmChildChannelInitializer(out, err, exitCode, runtimeEnvironment, broker);
    }

    @Override
    protected Logger log() {
        return LOG;
    }
}
