package org.drasyl.jtasklet.broker;

import io.netty.channel.ChannelHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import org.drasyl.cli.ChannelOptions;
import org.drasyl.handler.PeersRttReport;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.jtasklet.broker.channel.BrokerChannelInitializer;
import org.drasyl.jtasklet.broker.channel.BrokerChildChannelInitializer;
import org.drasyl.util.Worm;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import picocli.CommandLine.Command;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

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
    private final Map<IdentityPublicKey, TaskletVm> vms = new HashMap<>();

    public BrokerCommand() {
        super(new NioEventLoopGroup(1), new NioEventLoopGroup());
    }

    @Override
    public Integer call() {
//        parentGroup.scheduleWithFixedDelay(() -> {
//            LOG.info("VMs = {}", vms);
//        }, 0L, 5_000L, MILLISECONDS);
        return super.call();
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

    public static class TaskletVm {
        private final long benchmark;
        private long lastHeartbeatTime;
        private PeersRttReport rttReport;
        private boolean busy;
        private int computations = 0;
        private String token;

        public TaskletVm(final long benchmark) {
            this.benchmark = benchmark;
        }

        public void heartbeatReceived(final PeersRttReport rttReport, final String token) {
            this.rttReport = rttReport;
            lastHeartbeatTime = System.currentTimeMillis();
            if (!Objects.equals(this.token, token)) {
                busy = false;
            }
            this.token = token;
        }

        @Override
        public String toString() {
            return "TaskletVm{" +
                    "benchmark=" + benchmark +
                    "rttReport=" + rttReport +
                    ", stale=" + isStale() +
                    ", busy=" + isBusy() +
                    ", token=" + token +
                    '}';
        }

        public boolean isStale() {
            return timeSinceLastHeartbeat() >= 5_000L;
        }

        public long timeSinceLastHeartbeat() {
            return System.currentTimeMillis() - lastHeartbeatTime;
        }

        public boolean isBusy() {
            return busy;
        }

        public void markBusy() {
            this.busy = true;
        }

        public int getComputations() {
            return computations;
        }

        public long getBenchmark() {
            return benchmark;
        }

        public String getToken() {
            return token;
        }

        public void markIdle() {
            this.busy = false;
            this.computations++;
        }
    }
}
