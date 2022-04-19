package org.drasyl.jtasklet.broker.handler;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.drasyl.channel.DrasylChannel;
import org.drasyl.handler.discovery.AddPathAndSuperPeerEvent;
import org.drasyl.handler.discovery.RemoveSuperPeerAndPathEvent;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.jtasklet.broker.ResourceProvider;
import org.drasyl.jtasklet.broker.ResourceProvider.ProviderState;
import org.drasyl.jtasklet.broker.scheduler.RandomSchedulingStrategy;
import org.drasyl.jtasklet.broker.scheduler.SchedulingStrategy;
import org.drasyl.jtasklet.event.ConnectionClosed;
import org.drasyl.jtasklet.event.ConnectionEvent;
import org.drasyl.jtasklet.event.MessageReceived;
import org.drasyl.jtasklet.event.NodeOffline;
import org.drasyl.jtasklet.event.NodeOnline;
import org.drasyl.jtasklet.event.TaskletEvent;
import org.drasyl.jtasklet.message.RegisterProvider;
import org.drasyl.jtasklet.message.ResourceRequest;
import org.drasyl.jtasklet.message.ResourceResponse;
import org.drasyl.jtasklet.message.TaskExecuted;
import org.drasyl.jtasklet.message.TaskExecuting;
import org.drasyl.jtasklet.message.TaskOffloaded;
import org.drasyl.jtasklet.message.TaskReset;
import org.drasyl.jtasklet.message.TaskResultReceived;
import org.drasyl.jtasklet.message.TaskletMessage;
import org.drasyl.util.Pair;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.PrintStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;
import static java.util.Objects.requireNonNull;
import static org.drasyl.jtasklet.broker.handler.BrokerHandler.State.ONLINE;

public class BrokerHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(BrokerHandler.class);
    private static final Random RANDOM = new Random();
    private State state = State.STARTED;
    private final PrintStream out;
    private final PrintStream err;
    private final Set<DrasylAddress> superPeers = new HashSet<>();
    private final Map<DrasylAddress, ResourceProvider> providers = new HashMap<>();
    private final SchedulingStrategy schedulingStrategy = new RandomSchedulingStrategy();

    public BrokerHandler(final PrintStream out,
                         final PrintStream err) {
        this.out = requireNonNull(out);
        this.err = requireNonNull(err);
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        ctx.fireChannelActive();
        LOG.info("Start Broker {}.", ctx.channel().localAddress());
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
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
            LOG.info("Connection to Provider {} closed. Unregister Provider.", sender);
            providers.remove(sender);
            printResourceProviders();
        }
    }

    private void messageReceived(final DrasylChannel channel,
                                 final TaskletMessage msg) {
        final DrasylAddress sender = (DrasylAddress) channel.remoteAddress();

        if (msg instanceof RegisterProvider) {
            LOG.info("Provider {} registered: {}", sender, msg);
            ResourceProvider provider = new ResourceProvider(((RegisterProvider) msg).getBenchmark());
            providers.put(sender, provider);
            printResourceProviders();
        }
        else if (msg instanceof ResourceRequest) {
            LOG.info("Got resource request {} from Consumer {}.", msg, sender);

            LOG.info("Schedule request using {}.", schedulingStrategy);
            final Pair<DrasylAddress, ResourceProvider> result = schedulingStrategy.schedule(providers);
            final IdentityPublicKey publicKey = (IdentityPublicKey) result.first();
            final ResourceProvider vm = result.second();
            String token = null;
            if (vm != null) {
                token = vm.assigned(sender);
                printResourceProviders();
            }
            LOG.info("Request has been scheduled to Provider `{}`.", publicKey);

            final ResourceResponse response = new ResourceResponse(publicKey, token);
            LOG.info("Send Consumer {} the resource response {}.", sender, response);
            channel.writeAndFlush(response).addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    LOG.info("Response at Consumer {} arrived!", sender);
                }
                else {
                    LOG.info("Failed to sent response to Consumer {}.", sender, future.cause());
                    if (vm != null) {
                        LOG.info("Put Provider {} back to pool of idle Providers.", publicKey);
                        vm.reset();
                    }
                }
            });
        }
        else if (msg instanceof TaskOffloaded) {
            // msg from consumer
            LOG.info("Got {} from Consumer {}.", msg, sender);

            final Optional<ResourceProvider> optional = providers.values().stream().filter(p -> p.isAssignedTo(sender, ((TaskOffloaded) msg).getToken())).findFirst();
            if (optional.isPresent()) {
                final ResourceProvider provider = optional.get();
                if (provider.offloaded()) {
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
        else if (msg instanceof TaskExecuting) {
            // msg from provider
            LOG.info("Got {} from Provider {}.", msg, sender);

            final ResourceProvider provider = providers.get(sender);
            if (provider != null) {
                if (provider.token() != null && Objects.equals(((TaskExecuting) msg).getToken(), provider.token())) {
                    if (provider.executing()) {
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
        else if (msg instanceof TaskExecuted) {
            // msg from provider
            LOG.info("Got {} from Provider {}.", msg, sender);

            final ResourceProvider provider = providers.get(sender);
            if (provider != null) {
                if (provider.token() != null && Objects.equals(((TaskExecuted) msg).getToken(), provider.token())) {
                    if (provider.executed()) {
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
        else if (msg instanceof TaskResultReceived) {
            // msg from consumer
            LOG.info("Got {} from Consumer {}.", msg, sender);

            final Optional<ResourceProvider> optional = providers.values().stream().filter(p -> p.isAssignedTo(sender, ((TaskResultReceived) msg).getToken())).findFirst();
            if (optional.isPresent()) {
                final ResourceProvider provider = optional.get();
                if (provider.done()) {
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
        else if (msg instanceof TaskReset) {
            // msg from provider OR consumer
            LOG.info("Got {} from Provider OR Consumer {}.", msg, sender);

            final Optional<ResourceProvider> optional = providers.values().stream().filter(p -> p.token() != null && Objects.equals(p.token(), ((TaskReset) msg).getToken())).findFirst();
            if (optional.isPresent()) {
                final ResourceProvider provider = optional.get();
                if (provider.reset()) {
                    LOG.info("Changed state of Provider {} to {}.", provider, provider.state());
                    printResourceProviders();
                }
                else {
                    LOG.info("Reject message {} as state change from {} to {} is illegal.", msg, provider.state(), ProviderState.READY);
                }
            }
            else {
                LOG.info("Reject message {} as Provider {} and token {} are currently not assigned to any Consumer.", msg, sender, ((TaskReset) msg).getToken(), ProviderState.READY);
            }
        }
    }

    private void printResourceProviders() {
        final StringBuilder builder = new StringBuilder();

        // table header
        final ZonedDateTime zonedDateTime = ZonedDateTime.ofInstant(Instant.now(), Clock.systemDefaultZone().getZone());
        builder.append(String.format("Time: %-35s%n", RFC_1123_DATE_TIME.format(zonedDateTime)));
        builder.append(String.format("%-64s  %-6s  %-7s  %7s  %-9s  %5s  %-64s%n", "Resource Provider", "Tasks", "ErrRt", "Bnchmrk", "State", "LstStChg", "Assigned to"));

        // table body
        for (final Entry<DrasylAddress, ResourceProvider> entry : providers.entrySet()) {
            final DrasylAddress address = entry.getKey();
            final ResourceProvider vm = entry.getValue();

            // table row
            builder.append(String.format(
                    "%-64s  %6d  %,6.2f%%  %7d  %-9s  %s%3ds ago  %-64s%n",
                    address,
                    vm.succeededTasks(),
                    vm.errorRate(),
                    vm.benchmark(),
                    vm.state(),
                    vm.timeSinceLastStateChange() > 99_999 ? ">" : "",
                    Math.min(vm.timeSinceLastStateChange() / 1_000, 99),
                    vm.assignedTo() != null ? vm.assignedTo() : "-"
            ));
        }

        LOG.info("\n{}", builder.toString());
    }

    enum State {
        STARTED,
        ONLINE
    }
}
