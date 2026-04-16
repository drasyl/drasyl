package org.drasyl.jtasklet.consumer;

import io.netty.channel.ChannelHandler;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.drasyl.cli.ChannelOptions;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.jtasklet.channel.ChildChannelInitializer;
import org.drasyl.jtasklet.consumer.channel.VNMIFEConsumerChannelInitializer;
import org.drasyl.jtasklet.consumer.channel.VNMIFERelayOnlyConsumerChannelInitializer;
import org.drasyl.jtasklet.vnmife.VNMIFETaskPayload;
import org.drasyl.util.EventLoopGroupUtil;
import org.drasyl.util.Worm;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

@Command(
        name = "offload",
        description = {
                "Offloads a Tasklet",
        },
        showDefaultValues = true
)
public class OffloadCommand extends ChannelOptions {
    private static final Logger LOG = LoggerFactory.getLogger(OffloadCommand.class);
    private static final EventLoopGroup group = EventLoopGroupUtil.getBestEventLoopGroup(1);
    @Option(
            names = { "--broker" }
    )
    private IdentityPublicKey broker;
    @Option(
            names = { "--task" },
            required = true
    )
    private Path task;
    @Parameters
    List<Object> input;
    @Option(
            names = { "--cycles" },
            defaultValue = "1"
    )
    private int cycles;
    @Option(
            names = { "--tags" },
            description = "Tags of the VM.",
            paramLabel = "tag",
            defaultValue = "",
            split = ","
    )
    private List<String> tags;
    @Option(
            names = { "--priority" },
            defaultValue = "0"
    )
    private int priority;
    @Option(
            names = { "--relay-only" }
    )
    protected boolean relayOnly;
    @Option(
            names = { "--peers" },
            description = "List of proactive peers connections.",
            paramLabel = "<public-key>",
            defaultValue = "", // Provided by ChannelOptionsDefaultProvider
            split = ","
    )
    protected List<IdentityPublicKey> peers;
    @Option(
            names = { "--client-key-dir" }
    )
    private Path clientKeyDir;
    private String source;
    private VNMIFETaskPayload payload;

    public OffloadCommand() {
        super(new NioEventLoopGroup(1), new NioEventLoopGroup());
    }

    @Override
    public Integer call() {
        if (input == null) {
            input = List.of();
        }

        try {
            out.println("Task        : " + task);
            out.println("Input       : " + Arrays.toString(input.toArray()));
            source = task.toAbsolutePath().toString();
            payload = VNMIFETaskPayload.fromPath(task);
            if (clientKeyDir == null) {
                clientKeyDir = identityFile.toPath().toAbsolutePath().getParent().resolve(identityFile.getName() + ".vnmife-client-keys");
            }
            return super.call();
        }
        catch (final IOException e) {
            e.printStackTrace();
            return 1;
        }
    }

    @Override
    protected ChannelHandler getHandler(final Worm<Integer> exitCode, final Identity identity) {
        if (relayOnly) {
            return new VNMIFERelayOnlyConsumerChannelInitializer(identity, group, bindAddress, networkId, onlineTimeoutMillis, superPeers, out, !protocolArmDisabled, broker, source, input.toArray(), payload, clientKeyDir, cycles, tags, priority);
        }

        return new VNMIFEConsumerChannelInitializer(identity, group, bindAddress, networkId, onlineTimeoutMillis, superPeers, out, !protocolArmDisabled, broker, source, input.toArray(), payload, clientKeyDir, cycles, tags, priority, peers);
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
