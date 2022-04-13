package org.drasyl.jtasklet.consumer;

import io.netty.channel.ChannelHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import org.drasyl.cli.ChannelOptions;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.jtasklet.consumer.channel.ConsumerChannelInitializer;
import org.drasyl.jtasklet.consumer.channel.ConsumerChildChannelInitializer;
import org.drasyl.util.Worm;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;

@Command(
        name = "offload",
        description = {
                "Offloads a Tasklet",
        },
        showDefaultValues = true
)
public class OffloadCommand extends ChannelOptions {
    private static final Logger LOG = LoggerFactory.getLogger(OffloadCommand.class);
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
    private final AtomicReference<IdentityPublicKey> provider = new AtomicReference<>();
    private String source;
    private final AtomicReference<Instant> requestResourceTime = new AtomicReference<>();
    private final AtomicReference<Instant> resourceResponseTime = new AtomicReference<>();
    private final AtomicReference<Instant> offloadTaskTime = new AtomicReference<>();
    private final AtomicReference<Instant> returnResultTime = new AtomicReference<>();

    public OffloadCommand() {
        super(new NioEventLoopGroup(1), new NioEventLoopGroup());
    }

    @Override
    public Integer call() {
        try {
            source = Files.readString(task, UTF_8);
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
        return new ConsumerChildChannelInitializer(
                out,
                err,
                exitCode,
                broker,
                source,
                input.toArray(),
                provider,
                output -> {
                    out.println("Resource request  :  0");
                    out.println("Resource response : +" + Duration.between(requestResourceTime.get(), resourceResponseTime.get()).toMillis());
                    out.println("Offload task      : +" + Duration.between(resourceResponseTime.get(), offloadTaskTime.get()).toMillis());
                    out.println("Return result     : +" + Duration.between(offloadTaskTime.get(), returnResultTime.get()).toMillis());
                    out.println("Got result        : " + Arrays.toString(output));
                },
                requestResourceTime,
                resourceResponseTime,
                offloadTaskTime,
                returnResultTime
        );
    }

    @Override
    protected Logger log() {
        return LOG;
    }
}
