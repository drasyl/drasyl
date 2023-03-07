package org.drasyl.jtasklet.consumer;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.drasyl.cli.ChannelOptions;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.jtasklet.GreyFilter;
import org.drasyl.jtasklet.channel.ChildChannelInitializer;
import org.drasyl.jtasklet.consumer.channel.ConsumerChannelInitializer;
import org.drasyl.util.EventLoopGroupUtil;
import org.drasyl.util.Worm;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static java.nio.charset.StandardCharsets.UTF_8;

@Command(
        name = "grey-filter",
        description = {
                "Offloads a Grey Filter Tasklet",
        },
        showDefaultValues = true
)
public class GreyFilterCommand extends ChannelOptions {
    private static final Logger LOG = LoggerFactory.getLogger(GreyFilterCommand.class);
    private static final EventLoopGroup eventGroup = EventLoopGroupUtil.getBestEventLoopGroup(1);
    private final AtomicReference<IdentityPublicKey> provider = new AtomicReference<>();
    @Option(
            names = { "--broker" }
    )
    private IdentityPublicKey broker;
    @Option(
            names = { "--image" },
            defaultValue = "images/Risoni-Bowl.jpg"
    )
    private File image;
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
            names = { "--peers" },
            description = "List of proactive peers connections.",
            paramLabel = "<public-key>",
            defaultValue = "", // Provided by ChannelOptionsDefaultProvider
            split = ","
    )
    protected List<IdentityPublicKey> peers;
    private String source;
    private Object[] input;
    private int height;
    private int width;
    private final AtomicReference<Instant> requestResourceTime = new AtomicReference<>();
    private final AtomicReference<Instant> resourceResponseTime = new AtomicReference<>();
    private final AtomicReference<Instant> offloadTaskTime = new AtomicReference<>();
    private final AtomicReference<Instant> returnResultTime = new AtomicReference<>();
    private final AtomicReference<String> token = new AtomicReference<>();
    private final AtomicReference<Channel> brokerChannel = new AtomicReference<>();

    public GreyFilterCommand() {
        super(new NioEventLoopGroup(1), new NioEventLoopGroup());
    }

    @Override
    public Integer call() {
        try {
            source = Files.readString(Path.of("tasks", "greyFilter.js"), UTF_8);
            final GreyFilter greyFilter = new GreyFilter(image);
            input = greyFilter.getInput();
            height = greyFilter.getHeight();
            width = greyFilter.getWidth();

            return super.call();
        }
        catch (final IOException e) {
            e.printStackTrace();
            return 1;
        }
    }

    @Override
    protected ChannelHandler getHandler(final Worm<Integer> exitCode, final Identity identity) {
        return new ConsumerChannelInitializer(identity, eventGroup, bindAddress, networkId, onlineTimeoutMillis, superPeers, out, !protocolArmDisabled, broker, source, input, 1, tags, priority, peers);
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