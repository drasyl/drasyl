package org.drasyl.jtasklet.broker;

import io.netty.channel.ChannelHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import org.drasyl.cli.ChannelOptions;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.jtasklet.broker.channel.BrokerChannelInitializer;
import org.drasyl.jtasklet.broker.channel.BrokerChildChannelInitializer;
import org.drasyl.util.Worm;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import picocli.CommandLine.Command;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Command(
        name = "broker",
        description = {
                "Starts a broker",
                "The broker performs the resource matchmaking.",
                "For this Tasklet VMs need to register to this broker.",
                "If a Resource Consumer requests a resource, the broker will select a suitable VM."
        },
        showDefaultValues = true
)
public class BrokerCommand extends ChannelOptions {
    private static final Logger LOG = LoggerFactory.getLogger(BrokerCommand.class);
    private final Map<IdentityPublicKey, TaskletVm> vms = new ConcurrentHashMap<>();

    public BrokerCommand() {
        super(new NioEventLoopGroup(1), new NioEventLoopGroup());
    }

    @Override
    protected ChannelHandler getHandler(final Worm<Integer> exitCode, final Identity identity) {
        return new BrokerChannelInitializer(identity, bindAddress, networkId, onlineTimeoutMillis, superPeers, out, err, exitCode, !protocolArmDisabled, vms);
    }

    @Override
    protected ChannelHandler getChildHandler(final Worm<Integer> exitCode,
                                             final Identity identity) {
        return new BrokerChildChannelInitializer(out, err, exitCode, vms);
    }

    @Override
    protected Logger log() {
        return LOG;
    }
}
