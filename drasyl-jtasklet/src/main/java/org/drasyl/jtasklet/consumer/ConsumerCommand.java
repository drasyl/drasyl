package org.drasyl.jtasklet.consumer;

import io.netty.channel.ChannelHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import org.drasyl.cli.ChannelOptions;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.jtasklet.channel.ChildChannelInitializer;
import org.drasyl.jtasklet.consumer.channel.ConsumerChannelInitializer;
import org.drasyl.util.Worm;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

@Command(
        name = "consumer",
        description = {
                "Start a Resource Consumer",
                "The Consumer is capable to offload one Task per time."
        },
        showDefaultValues = true
)
public class ConsumerCommand extends ChannelOptions {
    private static final Logger LOG = LoggerFactory.getLogger(ConsumerCommand.class);
    @Option(
            names = { "--broker" }
    )
    private IdentityPublicKey broker;
    @Option(
            names = { "--submit-bind" },
            description = "Binds submit server to given IP and port. If no port is specified, a random free port will be used.",
            paramLabel = "<host>[:<port>]",
            defaultValue = "0.0.0.0:25421"
    )
    protected InetSocketAddress submitBindAddress;

    public ConsumerCommand() {
        super(new NioEventLoopGroup(1), new NioEventLoopGroup());
    }

    @Override
    protected ChannelHandler getHandler(final Worm<Integer> exitCode, final Identity identity) {
        return new ConsumerChannelInitializer(identity, bindAddress, networkId, onlineTimeoutMillis, superPeers, out, err, exitCode, !protocolArmDisabled, broker, submitBindAddress);
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
