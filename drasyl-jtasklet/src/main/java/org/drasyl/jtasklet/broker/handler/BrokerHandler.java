package org.drasyl.jtasklet.broker.handler;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import org.drasyl.channel.DrasylChannel;
import org.drasyl.handler.PeersRttHandler.PeersRttReport;
import org.drasyl.handler.discovery.AddPathAndSuperPeerEvent;
import org.drasyl.handler.discovery.RemoveSuperPeerAndPathEvent;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.jtasklet.broker.BrokerLoggableRecord;
import org.drasyl.jtasklet.broker.ResourceProvider;
import org.drasyl.jtasklet.broker.ResourceProvider.ProviderState;
import org.drasyl.jtasklet.message.DerivedKeyRequest;
import org.drasyl.jtasklet.message.DerivedKeyResponse;
import org.drasyl.jtasklet.message.KeyGenRequest;
import org.drasyl.jtasklet.message.KeyGenResponse;
import org.drasyl.jtasklet.broker.scheduler.SchedulingStrategy;
import org.drasyl.jtasklet.event.*;
import org.drasyl.jtasklet.message.*;
import org.drasyl.jtasklet.provider.runtime.VNMIFERuntimeEnvironment;
import org.drasyl.jtasklet.util.CsvLogger;
import org.drasyl.util.Pair;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.Map.Entry;

import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.jtasklet.broker.handler.BrokerHandler.State.ONLINE;

public class BrokerHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(BrokerHandler.class);
    private static final int STUCK_PROVIDER_TIMEOUT = 300_000;
    private static final int STATUS_LOG_INTERVAL = 5_000;
    private State state = State.STARTED;
    private final PrintStream out;
    private final Set<DrasylAddress> superPeers = new HashSet<>();
    private final Map<DrasylAddress, ResourceProvider> providers = new HashMap<>();
    private final Map<DrasylAddress, Channel> providerChannels = new HashMap<>();
    private final Set<DrasylAddress> consumersWaitingForResource = new HashSet<>();
    private final SchedulingStrategy schedulingStrategy;
    private final CsvLogger logger;
    private final Map<DrasylAddress, PeersRttReport> rttReports = new HashMap<>();
    private final VNMIFERuntimeEnvironment runtimeEnvironment = new VNMIFERuntimeEnvironment();
    private final EventExecutorGroup vnmifeExecutor = new DefaultEventExecutorGroup(1);
    private final Path authorityFile;
    private final int boundX;
    private final int boundY;
    private final int boundN;

    public BrokerHandler(final PrintStream out,
                         final DrasylAddress address,
                         final SchedulingStrategy schedulingStrategy,
                         final Path vnmifeDir,
                         final int boundX,
                         final int boundY,
                         final int boundN) {
        this.out = requireNonNull(out);
        logger = new CsvLogger("broker-" + address.toString().substring(0, 8) + ".csv");
        this.schedulingStrategy = requireNonNull(schedulingStrategy);
        this.authorityFile = requireNonNull(vnmifeDir).toAbsolutePath().resolve("authority.json");
        this.boundX = boundX;
        this.boundY = boundY;
        this.boundN = boundN;
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        ctx.fireChannelActive();
        LOG.info("Start Broker {}.", ctx.channel().localAddress());
        try {
            Files.createDirectories(authorityFile.getParent());
        }
        catch (final IOException e) {
            throw new IllegalStateException("Unable to create VNMIFE broker directory.", e);
        }

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
    public void channelInactive(final ChannelHandlerContext ctx) {
        vnmifeExecutor.shutdownGracefully();
        ctx.fireChannelInactive();
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
            if (!consumersWaitingForResource.contains(sender)) {
                LOG.info("Got resource request {} from Consumer {}.", msg, sender);
            }
            final BrokerLoggableRecord loggableRecord = new BrokerLoggableRecord(sender);

            if (!consumersWaitingForResource.contains(sender)) {
                LOG.info("Schedule request using {} strategy.", schedulingStrategy);
            }
            final Pair<DrasylAddress, ResourceProvider> result = schedulingStrategy.schedule(providers, rttReports, sender, ((ResourceRequest) msg).getTags(), ((ResourceRequest) msg).getPriority());
            final IdentityPublicKey publicKey = (IdentityPublicKey) result.first();
            final ResourceProvider vm = result.second();
            final String token = vm != null ? vm.token() : null;
            if (vm != null) {
                vm.taskAssigned(sender);
                printResourceProviders();
            }
            if (vm != null) {
                if (consumersWaitingForResource.remove(sender)) {
                    LOG.info("Resource for Consumer {} is available again. Scheduled to Provider {}.", sender, publicKey);
                }
                else {
                    LOG.info("Request of Consumer {} has been scheduled to Provider {}.", sender, publicKey);
                }
            }
            else if (consumersWaitingForResource.add(sender)) {
                LOG.info("No resource currently available for Consumer {}.", sender);
            }
            loggableRecord.assignResource(publicKey, vm != null ? vm.benchmark() : -1, token, vm != null ? vm.tags() : new ArrayList<>(), ((ResourceRequest) msg).getPriority());

            final ResourceResponse response = new ResourceResponse(publicKey, token);
            if (vm != null || !consumersWaitingForResource.contains(sender)) {
                LOG.info("Send Consumer {} the resource response {}.", sender, response);
            }
            channel.writeAndFlush(response).addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    if (vm != null || !consumersWaitingForResource.contains(sender)) {
                        LOG.info("Response at Consumer {} arrived!", sender);
                    }
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
        else if (state == ONLINE && msg instanceof KeyGenRequest) {
            LOG.info("Got {} from Consumer {}.", msg, sender);
            final KeyGenRequest request = (KeyGenRequest) msg;
            vnmifeExecutor.execute(() -> {
                final long startTime = System.nanoTime();
                final io.netty.util.concurrent.ScheduledFuture<?> statusFuture = channel.eventLoop().scheduleAtFixedRate(() -> {
                    final long elapsed = (System.nanoTime() - startTime) / 1_000_000;
                    LOG.info("VN-MIFE setup for Consumer {} still running after {}ms: clients={}, vecLen={}.", sender, elapsed, request.getClientIds().size(), request.getVecLen());
                }, STATUS_LOG_INTERVAL, STATUS_LOG_INTERVAL, MILLISECONDS);
                try {
                    LOG.info("Start VN-MIFE setup for Consumer {}: clients={}, vecLen={}, bounds=({}, {}, {}).", sender, request.getClientIds().size(), request.getVecLen(), boundX, boundY, boundN);
                    final Map<String, String> clientKeys = new LinkedHashMap<>();
                    for (final String clientId : request.getClientIds()) {
                        clientKeys.put(clientId, runtimeEnvironment.setup(authorityFile, clientId, request.getVecLen(), boundX, boundY, boundN));
                    }
                    final long duration = (System.nanoTime() - startTime) / 1_000_000;
                    LOG.info("Finished VN-MIFE setup for Consumer {} in {}ms. Exported {} client keys.", sender, duration, clientKeys.size());
                    final KeyGenResponse response = new KeyGenResponse(request.getClientIds(), clientKeys, boundX, boundY, boundN);
                    channel.writeAndFlush(response).addListener((ChannelFutureListener) future -> {
                        if (future.isSuccess()) {
                            LOG.info("Key response arrived at Consumer {}.", sender);
                        }
                        else {
                            LOG.info("Failed to send key response to Consumer {}.", sender, future.cause());
                        }
                    });
                }
                catch (final RuntimeException e) {
                    LOG.info("Failed to generate keys for Consumer {}.", sender, e);
                    channel.close();
                }
                finally {
                    statusFuture.cancel(false);
                }
            });
        }
        else if (state == ONLINE && msg instanceof DerivedKeyRequest) {
            LOG.info("Got {} from Provider {}.", msg, sender);
            final ResourceProvider provider = providers.get(sender);
            if (provider != null && provider.token() != null && Objects.equals(provider.token(), ((DerivedKeyRequest) msg).getToken())) {
                final DerivedKeyRequest request = (DerivedKeyRequest) msg;
                vnmifeExecutor.execute(() -> {
                    final long startTime = System.nanoTime();
                    final io.netty.util.concurrent.ScheduledFuture<?> statusFuture = channel.eventLoop().scheduleAtFixedRate(() -> {
                        final long elapsed = (System.nanoTime() - startTime) / 1_000_000;
                        final int weightRows = request.getWeights().size();
                        final int weightCols = weightRows > 0 ? request.getWeights().get(0).size() : 0;
                        LOG.info("VN-MIFE derive-key for Provider {} still running after {}ms: session={}, clients={}, weights={}x{}.", sender, elapsed, request.getSession(), request.getClientIds().size(), weightRows, weightCols);
                    }, STATUS_LOG_INTERVAL, STATUS_LOG_INTERVAL, MILLISECONDS);
                    try {
                        final int weightRows = request.getWeights().size();
                        final int weightCols = weightRows > 0 ? request.getWeights().get(0).size() : 0;
                        LOG.info("Start VN-MIFE derive-key for Provider {}: session={}, clients={}, weights={}x{}.", sender, request.getSession(), request.getClientIds().size(), weightRows, weightCols);
                        validateWeights(request.getWeights());
                        final String functionalKey = runtimeEnvironment.deriveKey(authorityFile, request.getClientIds(), request.getSession(), request.getWeights());
                        final long duration = (System.nanoTime() - startTime) / 1_000_000;
                        LOG.info("Finished VN-MIFE derive-key for Provider {} in {}ms.", sender, duration);
                        final DerivedKeyResponse response = new DerivedKeyResponse(functionalKey);
                        channel.writeAndFlush(response).addListener((ChannelFutureListener) future -> {
                            if (future.isSuccess()) {
                                LOG.info("Derived key response arrived at Provider {}.", sender);
                            }
                            else {
                                LOG.info("Failed to send derived key response to Provider {}.", sender, future.cause());
                            }
                        });
                    }
                    catch (final RuntimeException e) {
                        LOG.info("Failed to derive functional key for Provider {}.", sender, e);
                        channel.close();
                    }
                    finally {
                        statusFuture.cancel(false);
                    }
                });
            }
            else {
                LOG.info("Reject message {} as Provider {} and token {} are currently not assigned.", msg, sender, ((DerivedKeyRequest) msg).getToken());
            }
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

    private void validateWeights(final List<List<Integer>> weights) {
        int max = 0;
        for (final List<Integer> row : weights) {
            for (final Integer value : row) {
                if (value != null) {
                    max = Math.max(max, Math.abs(value));
                }
            }
        }

        if (max > boundY) {
            throw new IllegalArgumentException("weights contain value with abs=" + max + " but broker bound-y is only " + boundY + ".");
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
                    String.join(",", vm.tags())
            ));
        }

        LOG.info("\n{}", builder.toString());
    }

    enum State {
        STARTED,
        ONLINE
    }
}
