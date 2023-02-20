package org.drasyl.jtasklet.provider.handler;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.concurrent.ScheduledFuture;
import org.drasyl.channel.DrasylChannel;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.handler.PeersRttHandler.PeersRttReport;
import org.drasyl.handler.discovery.AddPathAndSuperPeerEvent;
import org.drasyl.handler.discovery.RemoveSuperPeerAndPathEvent;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.jtasklet.broker.ResourceProvider;
import org.drasyl.jtasklet.event.*;
import org.drasyl.jtasklet.message.*;
import org.drasyl.jtasklet.provider.ProviderLoggableRecord;
import org.drasyl.jtasklet.provider.runtime.ExecutionResult;
import org.drasyl.jtasklet.provider.runtime.RuntimeEnvironment;
import org.drasyl.jtasklet.util.CsvLogger;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.PrintStream;
import java.util.HashSet;
import java.util.Set;

import static io.netty.channel.ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.jtasklet.provider.handler.ProviderHandler.State.*;

public class ProviderHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(ProviderHandler.class);
    private static final int OFFLOAD_TASK_TIMEOUT = 30_000;
    private final CsvLogger logger;
    private State state = STARTED;
    private final PrintStream out;
    private final Set<DrasylAddress> superPeers = new HashSet<>();
    private final DrasylAddress broker;
    private final long benchmark;
    private final EventLoopGroup taskEventLoop = new NioEventLoopGroup(1);
    private final RuntimeEnvironment runtimeEnvironment;
    private ProviderLoggableRecord taskRecord;
    private DrasylChannel brokerChannel;
    private String token;
    private DrasylChannel consumerChannel;
    private DrasylAddress consumer;
    private ScheduledFuture<?> timeoutGuard;

    public ProviderHandler(final PrintStream out,
                           final DrasylAddress address,
                           final DrasylAddress broker,
                           final long benchmark,
                           final RuntimeEnvironment runtimeEnvironment) {
        this.out = requireNonNull(out);
        this.broker = requireNonNull(broker);
        this.benchmark = benchmark;
        this.runtimeEnvironment = requireNonNull(runtimeEnvironment);
        logger = new CsvLogger("provider-" + address.toString().substring(0, 8) + ".csv");
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        ctx.fireChannelActive();
        LOG.info("[{}] Start Provider {}.", state, ctx.channel().localAddress());
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        taskEventLoop.shutdownGracefully();
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
        else if (state != STARTED && state != ONLINE && state != BROKER_CONNECTION_ISSUED && state != CLOSED && brokerChannel != null && evt instanceof PeersRttReport) {
            LOG.debug("[{}] Got RTT report {}. Redirect to Broker {}", state, evt, broker);
            brokerChannel.writeAndFlush(new RttReport((PeersRttReport) evt)).addListener((ChannelFutureListener) future -> {
                if (!future.isSuccess()) {
                    LOG.info("[{}] Unable to send RTT report {} to Broker {}:", state, evt, broker, future.cause());
                }
            });
        }
        else if (evt instanceof TaskletEvent) {
            if (evt instanceof NodeOnline) {
                state = ONLINE;
                LOG.info("[{}] Provider online!", state);

                state = BROKER_CONNECTION_ISSUED;
                LOG.info("[{}] Connect to Broker {}.", state, broker);
                brokerChannel = new DrasylChannel((DrasylServerChannel) ctx.channel(), broker);
                ctx.pipeline().fireChannelRead(brokerChannel);
            }
            else if (evt instanceof ConnectionEvent) {
                connectionChanged(ctx, (ConnectionEvent) evt);
            }
            else if (evt instanceof MessageReceived) {
                messageReceived(ctx, ((MessageReceived<?>) evt).channel(), ((MessageReceived<?>) evt).msg());
            }
        }

        ctx.fireUserEventTriggered(evt);
    }

    private void connectionChanged(final ChannelHandlerContext ctx, final ConnectionEvent evt) {
        final DrasylAddress sender = evt.sender();

        if (evt instanceof ConnectionEstablished && sender.equals(broker)) {
            state = READY;
            LOG.info("[{}] Connection to Broker {} established.", state, broker);
            registerAtBroker(ctx);
        }
        else if (state != CLOSED && evt instanceof ConnectionFailed && sender.equals(broker)) {
            state = CLOSED;
            LOG.info("[{}] Failed to connect to Broker {}.", state, broker, ((ConnectionFailed) evt).cause());
            ctx.pipeline().close();
        }
        else if (state != CLOSED && evt instanceof ConnectionClosed && sender.equals(broker)) {
            state = CLOSED;
            LOG.info("[{}] Broker {} closed connection. Shutdown Provider.", state, broker);
            ctx.pipeline().close();
        }
        // beside the broker, only consumers will contact us
        else if (state == BROKER_REGISTERED && evt instanceof ConnectionEstablished && consumer == null) {
            state = CONSUMER_CONNECTION_ESTABLISHED;
            consumer = sender;
            LOG.info("[{}] Connection to Consumer {} established.", state, consumer);

            // apply timeout guard
            if (timeoutGuard != null) {
                LOG.error("timeoutGuard war nicht null");
            }
            timeoutGuard = ctx.executor().schedule(() -> {
                timeoutGuard = null;
                // inform broker
                final ProviderReset providerReset = new ProviderReset(ResourceProvider.randomToken());
                LOG.info("[{}] Consumer {} has sent task to us within {}ms. Reset our state at Broker {}.", state, consumer, OFFLOAD_TASK_TIMEOUT, providerReset);
                brokerChannel.writeAndFlush(providerReset).addListener((ChannelFutureListener) future -> {
                    if (future.isSuccess()) {
                        state = BROKER_REGISTERED;
                        consumer = null;
                        consumerChannel = null;
                        LOG.info("[{}] Broker {} informed. Send me tasks! I'm hungry!", state, broker);
                    }
                    else {
                        future.channel().close();
                    }
                });
            }, OFFLOAD_TASK_TIMEOUT, MILLISECONDS);
        }
        else if (state == TASK_SCHEDULED && evt instanceof ConnectionClosed && sender.equals(consumer)) {
            // Consumer has closed connection to us, while we're still processing the task.
            // As we're not able to kill the task execution, we have to wait for completion
            // (despite the fact that the Consumer no more interested in the result...).
            // So we have to wait for the execution to finish to inform the broker that we're
            // available again. Reset state here, so the after-execution code can detect this
            // situtation
            timeoutGuard.cancel(false);
            timeoutGuard = null;
            consumer = null;
            consumerChannel = null;
        }
    }

    private void messageReceived(final ChannelHandlerContext ctx,
                                 final DrasylChannel channel,
                                 final TaskletMessage msg) {
        final DrasylAddress sender = (DrasylAddress) channel.remoteAddress();

        if (state == CONSUMER_CONNECTION_ESTABLISHED && msg instanceof OffloadTask) {
            timeoutGuard.cancel(false);
            timeoutGuard = null;
            final TaskExecuting taskExecuting = new TaskExecuting(token);
            state = TASK_SCHEDULED;
            LOG.info("[{}] Got task {} from Consumer {}. Inform Broker {}. Schedule it.", state, msg, sender, taskExecuting);
            consumerChannel = channel;
            token = ((OffloadTask) msg).getToken();
            taskRecord = new ProviderLoggableRecord((DrasylAddress) ctx.channel().localAddress(), broker, benchmark, consumer, token, ((OffloadTask) msg).getSource(), ((OffloadTask) msg).getInput());

            // inform broker
            brokerChannel.writeAndFlush(taskExecuting).addListener(FIRE_EXCEPTION_ON_FAILURE);

            taskEventLoop.execute(() -> {
                // execute
                state = TASK_EXECUTING;
                LOG.info("[{}] Start executing of task {} from Consumer {}.", state, msg, sender);
                taskRecord.executing();
                final ExecutionResult result = runtimeEnvironment.execute(((OffloadTask) msg).getSource(), ((OffloadTask) msg).getInput());
                taskRecord.executed(result.getOutput(), result.getExecutionTime());
                state = TASK_EXECUTED;
                LOG.info("[{}] Execution of task {} from Consumer {} finished in {}ms.", state, msg, sender, result.getExecutionTime());

                if (consumerChannel != null) {
                    // return result
                    final ReturnResult response = new ReturnResult(result.getOutput(), result.getExecutionTime());
                    LOG.info("[{}] Send result {} back to Consumer {}.", state, response, sender);
                    consumerChannel.writeAndFlush(response).addListener((ChannelFutureListener) future -> {
                        if (future.isSuccess()) {
                            taskRecord.returnedResult();

                            // inform broker
                            final TaskExecuted taskExecuted = new TaskExecuted(token, ResourceProvider.randomToken());
                            brokerChannel.writeAndFlush(taskExecuted).addListener(FIRE_EXCEPTION_ON_FAILURE);

                            LOG.info("[{}] Result arrived at Consumer {}! Inform Broker {}. Close connection to Consumer.", state, sender, taskExecuted);
                            future.channel().close();
                        }
                        else {
                            final ProviderReset providerReset = new ProviderReset(ResourceProvider.randomToken());
                            LOG.info("[{}] Failed to send response {} to Consumer {}. Reset our state at Broker {}.", state, response, future.channel().remoteAddress(), providerReset);

                            // inform broker
                            brokerChannel.writeAndFlush(providerReset).addListener(FIRE_EXCEPTION_ON_FAILURE);
                        }

                        state = BROKER_REGISTERED;
                        consumer = null;
                        logger.log(taskRecord);
                        LOG.info("[{}] Send me tasks! I'm hungry!", state);
                    });
                }
                else {
                    // it seems that the consumer is no longer interested in the result...great...
                    final ProviderReset providerReset = new ProviderReset(ResourceProvider.randomToken());
                    LOG.info("[{}] Consumer {} is no longer connected to us. Reset our state at Broker {}.", state, sender, providerReset);

                    // inform broker
                    brokerChannel.writeAndFlush(providerReset).addListener((ChannelFutureListener) future -> {
                        if (future.isSuccess()) {
                            state = BROKER_REGISTERED;
                            consumer = null;
                            consumerChannel = null;
                            LOG.info("[{}] Broker {} informed. Send me tasks! I'm hungry!", state, broker);
                        }
                        else {
                            future.channel().close();
                        }
                    });
                }
            });
        }
    }

    private void registerAtBroker(final ChannelHandlerContext ctx) {
        token = ResourceProvider.randomToken();
        final RegisterProvider msg = new RegisterProvider(benchmark, token);
        LOG.info("Register {} at Broker {}.", msg, broker);
        brokerChannel.writeAndFlush(msg).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                state = BROKER_REGISTERED;
                LOG.info("[{}] Registration {} at Broker {} arrived!", state, msg, broker);
                LOG.info("[{}] Send me tasks! I'm hungry!", state);
            }
            else {
                state = CLOSED;
                LOG.info("[{}] Failed to send registration {} to Broker {}. Shutdown Provider.", state, msg, broker, future.cause());
                ctx.pipeline().close();
            }
        });
    }

    enum State {
        STARTED,
        ONLINE,
        BROKER_CONNECTION_ISSUED,
        READY,
        BROKER_REGISTERED,
        CONSUMER_CONNECTION_ISSUED,
        CONSUMER_CONNECTION_ESTABLISHED,
        CONSUMER_CONNECTION_CLOSED,
        CONSUMER_CONNECTION_FAILED,
        TASK_SCHEDULED,
        TASK_EXECUTING,
        TASK_EXECUTED,
        CLOSED
    }
}
