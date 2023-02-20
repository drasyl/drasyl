package org.drasyl.jtasklet.consumer.handler;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.concurrent.ScheduledFuture;
import org.drasyl.channel.DrasylChannel;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.handler.PeersRttHandler.PeersRttReport;
import org.drasyl.handler.discovery.AddPathAndSuperPeerEvent;
import org.drasyl.handler.discovery.RemoveSuperPeerAndPathEvent;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.jtasklet.consumer.ConsumerLoggableRecord;
import org.drasyl.jtasklet.event.ConnectionClosed;
import org.drasyl.jtasklet.event.ConnectionEstablished;
import org.drasyl.jtasklet.event.ConnectionEvent;
import org.drasyl.jtasklet.event.ConnectionFailed;
import org.drasyl.jtasklet.event.MessageReceived;
import org.drasyl.jtasklet.event.NodeOffline;
import org.drasyl.jtasklet.event.NodeOnline;
import org.drasyl.jtasklet.event.TaskletEvent;
import org.drasyl.jtasklet.message.OffloadTask;
import org.drasyl.jtasklet.message.ResourceRequest;
import org.drasyl.jtasklet.message.ResourceResponse;
import org.drasyl.jtasklet.message.ReturnResult;
import org.drasyl.jtasklet.message.RttReport;
import org.drasyl.jtasklet.message.TaskFailed;
import org.drasyl.jtasklet.message.TaskOffloaded;
import org.drasyl.jtasklet.message.TaskResultReceived;
import org.drasyl.jtasklet.message.TaskletMessage;
import org.drasyl.jtasklet.util.CsvLogger;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static io.netty.channel.ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.jtasklet.consumer.handler.ConsumerHandler.State.BROKER_CONNECTION_ISSUED;
import static org.drasyl.jtasklet.consumer.handler.ConsumerHandler.State.CLOSED;
import static org.drasyl.jtasklet.consumer.handler.ConsumerHandler.State.ONLINE;
import static org.drasyl.jtasklet.consumer.handler.ConsumerHandler.State.PROVIDER_CONNECTION_CLOSED;
import static org.drasyl.jtasklet.consumer.handler.ConsumerHandler.State.PROVIDER_CONNECTION_ESTABLISHED;
import static org.drasyl.jtasklet.consumer.handler.ConsumerHandler.State.PROVIDER_CONNECTION_FAILED;
import static org.drasyl.jtasklet.consumer.handler.ConsumerHandler.State.PROVIDER_CONNECTION_ISSUED;
import static org.drasyl.jtasklet.consumer.handler.ConsumerHandler.State.READY;
import static org.drasyl.jtasklet.consumer.handler.ConsumerHandler.State.RESOURCE_REQUESTED;
import static org.drasyl.jtasklet.consumer.handler.ConsumerHandler.State.RESOURCE_REQUESTING;
import static org.drasyl.jtasklet.consumer.handler.ConsumerHandler.State.RESULT_RECEIVED;
import static org.drasyl.jtasklet.consumer.handler.ConsumerHandler.State.STARTED;
import static org.drasyl.jtasklet.consumer.handler.ConsumerHandler.State.TASK_OFFLOADED;
import static org.drasyl.jtasklet.consumer.handler.ConsumerHandler.State.TASK_OFFLOADING;
import static org.drasyl.util.Preconditions.requirePositive;

public class ConsumerHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(ConsumerHandler.class);
    private static final int RESOURCE_REQUEST_TIMEOUT = 10_000;
    private static final int OFFLOAD_TASK_TIMEOUT = 60_000;
    private static final int RETRY_INTERVAL = 5_000;
    private final CsvLogger logger;
    private State state = STARTED;
    private final PrintStream out;
    private final IdentityPublicKey broker;
    private final Set<DrasylAddress> superPeers = new HashSet<>();
    private String token;
    private DrasylChannel brokerChannel;
    private IdentityPublicKey provider;
    private DrasylChannel providerChannel;
    private final String source;
    private final Object[] input;
    private int remainingCycles;
    private ConsumerLoggableRecord taskRecord;
    private ScheduledFuture<?> timeoutGuard;

    public ConsumerHandler(final PrintStream out,
                           final DrasylAddress address,
                           final IdentityPublicKey broker,
                           final String source,
                           final Object[] input,
                           final int remainingCycles) {
        this.out = requireNonNull(out);
        this.broker = requireNonNull(broker);
        this.source = requireNonNull(source);
        this.input = requireNonNull(input);
        this.remainingCycles = requirePositive(remainingCycles);
        logger = new CsvLogger("consumer-" + address.toString().substring(0, 8) + ".csv");
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        ctx.fireChannelActive();
        LOG.info("[{}] Start Consumer {}.", state, ctx.channel().localAddress());
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
            if (state != ONLINE && evt instanceof NodeOnline) {
                state = ONLINE;
                LOG.info("[{}] Consumer online!", state);

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

        if (sender.equals(broker)) {
            if (state == BROKER_CONNECTION_ISSUED && evt instanceof ConnectionEstablished) {
                state = READY;
                LOG.info("[{}] Connection to Broker {} established.", state, broker);
                requestResource(ctx);
            }
            else if (state == BROKER_CONNECTION_ISSUED && evt instanceof ConnectionFailed) {
                state = CLOSED;
                LOG.info("[{}] Failed to connect to Broker {}. Shutdown Consumer.", state, broker, ((ConnectionFailed) evt).cause());
                ctx.channel().close();
            }
            else if (state != CLOSED && evt instanceof ConnectionClosed) {
                state = CLOSED;
                LOG.info("[{}] Broker {} closed connection. Shutdown Consumer.", state, broker);
                ctx.channel().close();
            }
        }
        else if (sender.equals(provider)) {
            if (state == PROVIDER_CONNECTION_ISSUED && evt instanceof ConnectionEstablished) {
                state = PROVIDER_CONNECTION_ESTABLISHED;
                LOG.info("[{}] Connection to Provider {} established.", state, broker);
                offloadTask(ctx);
            }
            else if (state == PROVIDER_CONNECTION_ISSUED && evt instanceof ConnectionFailed) {
                state = PROVIDER_CONNECTION_FAILED;
                final TaskFailed taskFailed = new TaskFailed(token);
                LOG.info("[{}] Failed to connect to Provider {}. Inform Broker.", state, provider, ((ConnectionFailed) evt).cause());

                provider = null;
                providerChannel = null;
                if (timeoutGuard != null) {
                    timeoutGuard.cancel(false);
                    timeoutGuard = null;
                }

                // inform broker & retry
                brokerChannel.writeAndFlush(taskFailed).addListener((ChannelFutureListener) future -> {
                    LOG.info("[{}] Broker {} informed. Now retry.", state, broker, taskFailed);
                    state = READY;
                    requestResource(ctx);
                });
            }
            else if (state == TASK_OFFLOADED && evt instanceof ConnectionClosed) {
                state = PROVIDER_CONNECTION_CLOSED;
                final TaskFailed taskFailed = new TaskFailed(token);
                LOG.info("[{}] Provider {} closed connection. Inform Broker {}.", state, provider, taskFailed);

                provider = null;
                providerChannel = null;
                if (timeoutGuard != null) {
                    timeoutGuard.cancel(false);
                    timeoutGuard = null;
                }

                // inform broker & retry
                brokerChannel.writeAndFlush(taskFailed).addListener((ChannelFutureListener) future -> {
                    LOG.info("[{}] Broker {} informed. Now retry.", state, broker, taskFailed);
                    state = READY;
                    requestResource(ctx);
                });
            }
        }
    }

    private void messageReceived(final ChannelHandlerContext ctx,
                                 final DrasylChannel channel,
                                 final TaskletMessage msg) {
        final DrasylAddress sender = (DrasylAddress) channel.remoteAddress();

        if (sender.equals(broker)) {
            if (state == RESOURCE_REQUESTED && msg instanceof ResourceResponse) {
                gotResourceResponse(ctx, msg, sender);
            }
        }
        else if (sender.equals(provider)) {
            if (state == TASK_OFFLOADED && msg instanceof ReturnResult) {
                gotTaskReturn(ctx, msg, sender);
            }
        }
    }

    private void requestResource(final ChannelHandlerContext ctx) {
        state = RESOURCE_REQUESTING;
        LOG.info("[{}] Request resource at Broker {}.", state, broker);
        this.taskRecord = new ConsumerLoggableRecord((DrasylAddress) ctx.channel().localAddress(), broker, source, input);
        final ResourceRequest request = new ResourceRequest();
        brokerChannel.writeAndFlush(request).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                state = RESOURCE_REQUESTED;
                LOG.info("[{}] Request {} at Broker {} arrived!", state, request, broker);
                taskRecord.resourceRequested();
            }
            else {
                state = CLOSED;
                LOG.info("[{}] Failed to sent request {} to Broker {}. Shutdown Consumer.", state, request, broker, future.cause());
                ctx.channel().close();
            }
        });

        // apply timeout guard
        if (timeoutGuard != null) {
            timeoutGuard.cancel(false);
            timeoutGuard = null;
        }
        timeoutGuard = ctx.executor().schedule(() -> {
            timeoutGuard = null;
            if (state == RESOURCE_REQUESTING) {
                state = CLOSED;
                LOG.info("[{}] Broker {} has not responded to our request {} within {}ms. Shutdown Consumer.", state, broker, request, RESOURCE_REQUEST_TIMEOUT);
                ctx.channel().close();
            }
        }, RESOURCE_REQUEST_TIMEOUT, MILLISECONDS);
    }

    private void gotResourceResponse(final ChannelHandlerContext ctx,
                                     final TaskletMessage msg,
                                     final DrasylAddress sender) {
        LOG.info("[{}] Got resource response {} from Broker {}.", state, msg, sender);
        if (timeoutGuard != null) {
            timeoutGuard.cancel(false);
            timeoutGuard = null;
        }
        token = ((ResourceResponse) msg).getToken();
        provider = ((ResourceResponse) msg).getPublicKey();
        taskRecord.resourceResponded(provider, token);
        if (provider == null) {
            LOG.info("[{}] Broker has not found any resource for us. Retry in {}ms. {} cycles remaining.", state, RETRY_INTERVAL, remainingCycles);
            logger.log(taskRecord);

            out.println("No resources for offloading available. Retry in " + RETRY_INTERVAL + "ms. " + remainingCycles + " cycles remaining.");
            state = READY;
            ctx.executor().schedule(() -> requestResource(ctx), RETRY_INTERVAL, MILLISECONDS);

            return;
        }

        state = PROVIDER_CONNECTION_ISSUED;
        LOG.info("[{}] Connect to Provider {}.", state, provider);
        providerChannel = new DrasylChannel((DrasylServerChannel) ctx.channel(), provider);
        ctx.pipeline().fireChannelRead(providerChannel);
    }

    private void offloadTask(final ChannelHandlerContext ctx) {
        final OffloadTask msg = new OffloadTask(token, source, input);
        state = TASK_OFFLOADING;
        LOG.info("[{}] Offload task {} to Provider {}.", state, msg, provider);
        taskRecord.offloadTask();
        providerChannel.writeAndFlush(msg).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                final TaskOffloaded taskOffloaded = new TaskOffloaded(token);
                state = TASK_OFFLOADED;
                LOG.info("[{}] Task arrived at Provider {}! Inform Broker {}.", state, provider, taskOffloaded);
                taskRecord.offloadedTask();

                // inform broker
                brokerChannel.writeAndFlush(taskOffloaded).addListener(FIRE_EXCEPTION_ON_FAILURE);
            }
            else {
                final TaskFailed taskFailed = new TaskFailed(token);
                LOG.info("[{}] Failed to offload task {} to Provider {}. Inform Broker {}.", state, msg, provider, taskFailed, future.cause());

                provider = null;
                providerChannel = null;
                if (timeoutGuard != null) {
                    timeoutGuard.cancel(false);
                    timeoutGuard = null;
                }

                // inform broker
                brokerChannel.writeAndFlush(taskFailed).addListener((ChannelFutureListener) future2 -> {
                    LOG.info("[{}] Broker {} informed. Now retry.", state, broker, taskFailed);
                    state = READY;
                    requestResource(ctx);
                });
            }
        });

        // apply timeout guardm
        if (timeoutGuard != null) {
            timeoutGuard.cancel(false);
            timeoutGuard = null;
        }
        timeoutGuard = ctx.executor().schedule(() -> {
            timeoutGuard = null;
            // inform broker
            final TaskFailed taskFailed = new TaskFailed(token);
            LOG.info("[{}] Provider {} has not provided results for our task {} within {}ms. Inform Broker {}.", state, provider, msg, OFFLOAD_TASK_TIMEOUT, taskFailed);
            brokerChannel.writeAndFlush(taskFailed).addListener((ChannelFutureListener) future -> {
                state = CLOSED;
                LOG.info("[{}] Broker {} informed. Shutdown Consumer.", state, broker, taskFailed);
                ctx.channel().close();
            });
        }, OFFLOAD_TASK_TIMEOUT, MILLISECONDS);
    }

    private void gotTaskReturn(final ChannelHandlerContext ctx,
                               final TaskletMessage msg,
                               final DrasylAddress sender) {
        final TaskResultReceived taskResultReceived = new TaskResultReceived(token);
        state = RESULT_RECEIVED;
        LOG.info("[{}] Got result {} from Provider {}. Inform Broker {}.", state, msg, sender, taskResultReceived);
        if (timeoutGuard != null) {
            timeoutGuard.cancel(false);
            timeoutGuard = null;
        }
        taskRecord.resultReturned(((ReturnResult) msg).getOutput(), ((ReturnResult) msg).getExecutionTime());

        out.println("Output      : " + Arrays.toString(((ReturnResult) msg).getOutput()));
        logger.log(taskRecord);

        // inform broker
        final ChannelFuture informBrokerFuture = brokerChannel.writeAndFlush(taskResultReceived);

        out.println("Rem. Cycles : " + (remainingCycles - 1));
        if (--remainingCycles > 0) {
            LOG.info("[{}] Before performing the next cycle. Close connection to Provider {} first.", state, provider);

            // close channel to provider first
            providerChannel.close().addListener((ChannelFutureListener) future -> {
                state = READY;
                if (future.isSuccess()) {
                    LOG.info("[{}] Connection to Provider {} closed.", state, provider);
                }
                else {
                    LOG.info("[{}] Failed to close connection to Provider {} closed:", state, provider, future.cause());
                }
                provider = null;
                providerChannel = null;
                requestResource(ctx);
            });
        }
        else {
            state = CLOSED;
            LOG.info("[{}] Close connection to Broker {}.", state, broker);

            // close consumer afterwards
            informBrokerFuture.addListener((ChannelFutureListener) future -> {
                LOG.info("[{}] Connection to Broker {} closed. Shutdown Consumer.", state, broker, future.channel().remoteAddress());
                ctx.channel().close();
            });
        }
    }

    enum State {
        STARTED,
        ONLINE,
        BROKER_CONNECTION_ISSUED,
        READY,
        RESOURCE_REQUESTING,
        RESOURCE_REQUESTED,
        PROVIDER_CONNECTION_ISSUED,
        PROVIDER_CONNECTION_ESTABLISHED,
        PROVIDER_CONNECTION_CLOSED,
        PROVIDER_CONNECTION_FAILED,
        TASK_OFFLOADING,
        TASK_OFFLOADED,
        RESULT_RECEIVED,
        CLOSED
    }
}
