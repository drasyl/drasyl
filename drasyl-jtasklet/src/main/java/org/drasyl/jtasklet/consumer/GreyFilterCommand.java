package org.drasyl.jtasklet.consumer;

import io.netty.channel.ChannelHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import org.drasyl.cli.ChannelOptions;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.jtasklet.GreyFilter;
import org.drasyl.jtasklet.consumer.channel.ConsumerChannelInitializer;
import org.drasyl.jtasklet.consumer.channel.ConsumerChildChannelInitializer;
import org.drasyl.util.Worm;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static java.nio.charset.StandardCharsets.UTF_8;

@Command(
        name = "greyfilter",
        description = {
                "Offloads a Greyfilter Tasklet",
        },
        showDefaultValues = true
)
public class GreyFilterCommand extends ChannelOptions {
    private static final Logger LOG = LoggerFactory.getLogger(GreyFilterCommand.class);
    private final AtomicReference<IdentityPublicKey> provider = new AtomicReference<>();
    @Option(
            names = { "--broker" }
    )
    private IdentityPublicKey broker;
    @Option(
            names = { "--image" },
            defaultValue = "images/3phases.jpg"
    )
    private File image;
    private String source;
    private Object[] input;

    public GreyFilterCommand() {
        super(new NioEventLoopGroup(1), new NioEventLoopGroup());
    }

    @Override
    public Integer call() {
        try {
            source = Files.readString(Path.of("tasks", "greyfilter.js"), UTF_8);
            final GreyFilter greyFilter = new GreyFilter(image);
            input = greyFilter.getInput();

            return super.call();
        }
        catch (final IOException e) {
            e.printStackTrace();
            return 1;
        }
    }

    @Override
    protected ChannelHandler getHandler(final Worm<Integer> exitCode, final Identity identity) {
        return new ConsumerChannelInitializer(identity, bindAddress, networkId, onlineTimeoutMillis, superPeers, out, err, exitCode, !protocolArmDisabled, broker);
    }

    @Override
    protected ChannelHandler getChildHandler(final Worm<Integer> exitCode,
                                             final Identity identity) {
        return new ConsumerChildChannelInitializer(out, err, exitCode, broker, source, input, provider);
    }

    @Override
    protected Logger log() {
        return LOG;
    }
}
