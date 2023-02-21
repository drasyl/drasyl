package org.drasyl.jtasklet.broker.handler;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.drasyl.channel.DrasylChannel;
import org.drasyl.handler.PeersRttHandler.PeersRttReport;
import org.drasyl.handler.discovery.AddPathAndSuperPeerEvent;
import org.drasyl.handler.discovery.RemoveSuperPeerAndPathEvent;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.jtasklet.broker.BrokerLoggableRecord;
import org.drasyl.jtasklet.broker.ResourceProvider;
import org.drasyl.jtasklet.broker.ResourceProvider.ProviderState;
import org.drasyl.jtasklet.broker.scheduler.SchedulingStrategy;
import org.drasyl.jtasklet.event.*;
import org.drasyl.jtasklet.message.*;
import org.drasyl.jtasklet.util.CsvLogger;
import org.drasyl.util.Pair;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.PrintStream;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.Map.Entry;

import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.jtasklet.broker.handler.BrokerHandler.State.ONLINE;

public class BrokerHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(BrokerHandler.class);
    private static final int STUCK_PROVIDER_TIMEOUT = 60_000;
    private State state = State.STARTED;
    private final PrintStream out;
    private final Set<DrasylAddress> superPeers = new HashSet<>();
    private final Map<DrasylAddress, ResourceProvider> providers = new HashMap<>();
    private final Map<DrasylAddress, Channel> providerChannels = new HashMap<>();
    private final SchedulingStrategy schedulingStrategy;
    private final CsvLogger logger;
    private final Map<DrasylAddress, PeersRttReport> rttReports = new HashMap<>();
    private BrokerLoggableRecord loggableRecord;

    public BrokerHandler(final PrintStream out,
                         final DrasylAddress address,
                         final SchedulingStrategy schedulingStrategy) {
        this.out = requireNonNull(out);
        logger = new CsvLogger("broker-" + address.toString().substring(0, 8) + ".csv");
        this.schedulingStrategy = requireNonNull(schedulingStrategy);
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        ctx.fireChannelActive();
        LOG.info("Start Broker {}.", ctx.channel().localAddress());

        ctx.executor().scheduleWithFixedDelay(() -> {
            // kick provider that are (potentially?) stuck in a non-READY state
            final Set<DrasylAddress> stuckProviders = new HashSet<>();
            providers.forEach((address, provider) -> {
                if (provider.state() != ProviderState.READY && provider.timeSinceLastStateChange() >= STUCK_PROVIDER_TIMEOUT) {
                    stuckProviders.add(address);
                }
            });
            if (!stuckProviders.isEmpty()) {
                stuckProviders.forEach(address -> {
                    LOG.info("Unregister Provider {} that is stuck in non-READY for more then {}ms.", address, STUCK_PROVIDER_TIMEOUT);
                    providers.remove(address);
                    providerChannels.remove(address).close();
                });
                printResourceProviders();
            }
        }, 5_000, 5_000, MILLISECONDS);
    }

    @Override
    public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) {
        if (evt instanceof AddPathAndSuperPeerEvent) {
            if (superPeers.add(((AddPathAndSuperPeerEvent) evt).getAddress()) && superPeers.size() == 1) {
                ctx.pipeline().fireUserEventTriggered(new NodeOnline());
            }
        }
        else if (evt instanceof RemoveSuperPeerAndPathEvent) {
            if (superPeers.remove(((RemoveSuperPeerAndPathEvent) evt).getAddress()) && superPeers.isEmpty()) {
                ctx.pipeline().fireUserEventTriggered(new NodeOffline());
            }
        }
        else if (evt instanceof TaskletEvent) {
            if (evt instanceof NodeOnline) {
                state = ONLINE;
                LOG.info("Broker online!");
            }
            else if (evt instanceof ConnectionEvent) {
                connectionChanged((ConnectionEvent) evt);
            }
            else if (evt instanceof MessageReceived) {
                messageReceived(((MessageReceived<?>) evt).channel(), ((MessageReceived<?>) evt).msg());
            }
        }

        ctx.fireUserEventTriggered(evt);
    }

    private void connectionChanged(final ConnectionEvent evt) {
        final DrasylAddress sender = evt.sender();

        if (evt instanceof ConnectionClosed && providers.containsKey(sender)) {
            LOG.info("Unregister Provider {} as connection has been closed.", sender);
            providers.remove(sender);
            providerChannels.remove(sender);
            printResourceProviders();
        }
    }

    private void messageReceived(final DrasylChannel channel,
                                 final TaskletMessage msg) {
        final DrasylAddress sender = (DrasylAddress) channel.remoteAddress();

        if (state == ONLINE && msg instanceof RegisterProvider) {
            LOG.info("Provider {} registered: {}", sender, msg);
            final ResourceProvider provider = new ResourceProvider(((RegisterProvider) msg).getBenchmark(), ((RegisterProvider) msg).getToken(), ((RegisterProvider) msg).getTags());
            providers.put(sender, provider);
            providerChannels.put(sender, channel);
            printResourceProviders();
        }
        else if (state == ONLINE && msg instanceof ResourceRequest) {
            LOG.info("Got resource request {} from Consumer {}.", msg, sender);
            loggableRecord = new BrokerLoggableRecord(sender);

            LOG.info("Schedule request using {} strategy.", schedulingStrategy);
            final Pair<DrasylAddress, ResourceProvider> result = schedulingStrategy.schedule(providers, rttReports, sender, ((ResourceRequest) msg).getTags(), ((ResourceRequest) msg).getPriority());
            final IdentityPublicKey publicKey = (IdentityPublicKey) result.first();
            final ResourceProvider vm = result.second();
            final String token = vm != null ? vm.token() : null;
            if (vm != null) {
                vm.taskAssigned(sender);
                printResourceProviders();
            }
            LOG.info("Request of Consumer {} has been scheduled to Provider {}.", sender, publicKey);
            loggableRecord.assignResource(publicKey, vm != null ? vm.benchmark() : -1, token, vm != null ? vm.tags() : new String[]{}, ((ResourceRequest) msg).getPriority());

            final ResourceResponse response = new ResourceResponse(publicKey, token);
            LOG.info("Send Consumer {} the resource response {}.", sender, response);
            channel.writeAndFlush(response).addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    LOG.info("Response at Consumer {} arrived!", sender);
                    loggableRecord.resourceResponded();
                }
                else {
                    LOG.info("Failed to sent response to Consumer {}.", sender, future.cause());
                    if (vm != null) {
                        LOG.info("Put Provider {} back to pool of idle Providers.", publicKey);
                        // re-use old token. This may be a vulnerability as malicious Consumers can
                        // retrieve token and then pretends to not retrieved it by not sending any
                        // ACKs
                        vm.providerReset(token);
                    }
                }
                logger.log(loggableRecord);
            });
        }
        else if (state == ONLINE && msg instanceof TaskOffloaded) {
            // msg from consumer
            LOG.info("Got {} from Consumer {}.", msg, sender);

            final Optional<ResourceProvider> optional = providers.values().stream().filter(p -> p.isAssignedTo(sender, ((TaskOffloaded) msg).getToken())).findFirst();
            if (optional.isPresent()) {
                final ResourceProvider provider = optional.get();
                if (provider.taskOffloaded()) {
                    LOG.info("Changed state of Provider {} to {}.", provider, provider.state());
                    printResourceProviders();
                }
                else {
                    LOG.info("Reject message {} as state change from {} to {} is illegal.", msg, provider.state(), ProviderState.OFFLOADED);
                }
            }
            else {
                LOG.info("Reject message {} as Provider {} and token {} are currently not assigned to any Consumer.", msg, sender, ((TaskOffloaded) msg).getToken(), ProviderState.OFFLOADED);
            }
        }
        else if (state == ONLINE && msg instanceof TaskExecuting) {
            // msg from provider
            LOG.info("Got {} from Provider {}.", msg, sender);

            final ResourceProvider provider = providers.get(sender);
            if (provider != null) {
                if (provider.token() != null && Objects.equals(((TaskExecuting) msg).getToken(), provider.token())) {
                    if (provider.taskExecuting()) {
                        LOG.info("Changed state of Provider {} to {}.", provider, provider.state());
                        printResourceProviders();
                    }
                    else {
                        LOG.info("Reject message {} as state change from {} to {} is illegal.", msg, provider.state(), ProviderState.EXECUTING);
                    }
                }
                else {
                    LOG.info("Reject message {} as actual token {} does not match expected token {}.", msg, ((TaskExecuting) msg).getToken(), provider.token());
                }
            }
            else {
                LOG.info("Reject message {} as {} is no Provider.", msg, sender);
            }
        }
        else if (state == ONLINE && msg instanceof TaskExecuted) {
            // msg from provider
            LOG.info("Got {} from Provider {}.", msg, sender);

            final ResourceProvider provider = providers.get(sender);
            if (provider != null) {
                if (provider.token() != null && Objects.equals(((TaskExecuted) msg).getToken(), provider.token())) {
                    if (provider.taskExecuted(((TaskExecuted) msg).getNextToken())) {
                        LOG.info("Changed state of Provider {} to {}.", provider, provider.state());
                        printResourceProviders();
                    }
                    else {
                        LOG.info("Reject message {} as state change from {} to {} is illegal.", msg, provider.state(), ProviderState.EXECUTING);
                    }
                }
                else {
                    LOG.info("Reject message {} as actual token {} does not match expected token {}.", msg, ((TaskExecuted) msg).getToken(), provider.token());
                }
            }
            else {
                LOG.info("Reject message {} as {} is no Provider.", msg, sender);
            }
        }
        else if (state == ONLINE && msg instanceof TaskResultReceived) {
            // msg from consumer
            LOG.info("Got {} from Consumer {}.", msg, sender);

            final Optional<ResourceProvider> optional = providers.values().stream().filter(p -> p.isAssignedTo(sender, ((TaskResultReceived) msg).getToken())).findFirst();
            if (optional.isPresent()) {
                final ResourceProvider provider = optional.get();
                if (provider.taskDone()) {
                    LOG.info("Changed state of Provider {} to {}.", provider, provider.state());
                    printResourceProviders();
                }
                else {
                    LOG.info("Reject message {} as state change from {} to {} is illegal.", msg, provider.state(), ProviderState.READY);
                }
            }
            else {
                LOG.info("Reject message {} as Provider {} and token {} are currently not assigned to any Consumer.", msg, sender, ((TaskResultReceived) msg).getToken(), ProviderState.READY);
            }
        }
        else if (state == ONLINE && msg instanceof TaskFailed) {
            // msg from consumer
            LOG.info("Got {} from Consumer {}.", msg, sender);

            final Optional<ResourceProvider> optional = providers.values().stream().filter(p -> p.token() != null && Objects.equals(p.token(), ((TaskFailed) msg).getToken())).findFirst();
            if (optional.isPresent()) {
                final ResourceProvider provider = optional.get();
                if (provider.taskFailed()) {
                    LOG.info("Changed state of Provider {} to {}.", provider, provider.state());
                    printResourceProviders();
                }
                else {
                    LOG.info("Reject message {} as state change from {} to {} is illegal.", msg, provider.state(), ProviderState.READY);
                }
            }
            else {
                LOG.info("Reject message {} as Provider {} and token {} are currently not assigned to any Consumer.", msg, sender, ((TaskFailed) msg).getToken(), ProviderState.READY);
            }
        }
        else if (state == ONLINE && msg instanceof ProviderReset) {
            // msg from provider
            LOG.info("Got {} from Provider {}.", msg, sender);

            final ResourceProvider provider = providers.get(sender);
            if (provider != null) {
                provider.providerReset(((ProviderReset) msg).getNewToken());
                LOG.info("Changed state of Provider {} to {}.", provider, provider.state());
                printResourceProviders();
            }
            else {
                LOG.info("Reject message {} as {} is no Provider.", msg, sender);
            }
        }
        else if (msg instanceof RttReport) {
            LOG.debug("Got RTT report {} from {}.", msg, sender);
            rttReports.put(sender, ((RttReport) msg).getReport());
        }
    }

    private void printResourceProviders() {
        final StringBuilder builder = new StringBuilder();

        // table header
        builder.append(String.format("Time: %-35s%n", RFC_1123_DATE_TIME.format(ZonedDateTime.now())));
        builder.append(String.format("%-64s  %-6s  %-7s  %7s  %-9s  %8s  %-64s  %-6s %-64s%n", "Resource Provider", "Tasks", "ErrRt", "Bnchmrk", "State", "LstStChg", "Assigned To", "Token", "Tags"));

        // table body
        for (final Entry<DrasylAddress, ResourceProvider> entry : providers.entrySet()) {
            final DrasylAddress address = entry.getKey();
            final ResourceProvider vm = entry.getValue();

            // table row
            builder.append(String.format(
                    "%-64s  %6d  %,6.2f%%  %7d  %-9s  %s%2ds ago  %-64s  %-6s %-64s%n",
                    address,
                    vm.succeededTasks() + vm.failedTasks(),
                    vm.errorRate() * 100,
                    vm.benchmark(),
                    vm.state(),
                    vm.timeSinceLastStateChange() > 99_999 ? ">" : " ",
                    Math.min(vm.timeSinceLastStateChange() / 1_000, 99),
                    vm.assignedTo() != null ? vm.assignedTo() : "-",
                    vm.token(),
                    Arrays.toString(vm.tags())
            ));
        }

        LOG.info("\n{}", builder.toString());
    }

    enum State {
        STARTED,
        ONLINE
    }
}
