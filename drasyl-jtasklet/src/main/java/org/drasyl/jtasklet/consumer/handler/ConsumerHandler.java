package org.drasyl.jtasklet.consumer.handler;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.concurrent.ScheduledFuture;
import org.drasyl.channel.DrasylChannel;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.handler.discovery.AddPathAndSuperPeerEvent;
import org.drasyl.handler.discovery.RemoveSuperPeerAndPathEvent;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.jtasklet.consumer.ConsumerTaskRecord;
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
import static org.drasyl.jtasklet.consumer.handler.ConsumerHandler.State.BROKER_CONNECTION_ESTABLISHED;
import static org.drasyl.jtasklet.consumer.handler.ConsumerHandler.State.BROKER_CONNECTION_ISSUED;
import static org.drasyl.jtasklet.consumer.handler.ConsumerHandler.State.CLOSED;
import static org.drasyl.jtasklet.consumer.handler.ConsumerHandler.State.PROVIDER_CONNECTION_ESTABLISHED;
import static org.drasyl.jtasklet.consumer.handler.ConsumerHandler.State.PROVIDER_CONNECTION_ISSUED;
import static org.drasyl.jtasklet.consumer.handler.ConsumerHandler.State.RESOURCE_REQUESTED;
import static org.drasyl.jtasklet.consumer.handler.ConsumerHandler.State.TASK_OFFLOADED;

public class ConsumerHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(ConsumerHandler.class);
    private static final int RESOURCE_REQUEST_TIMEOUT = 10_000;
    private static final int OFFLOAD_TASK_TIMEOUT = 60_000;
    private final CsvLogger logger;
    private State state = State.STARTED;
    private final PrintStream out;
    private final PrintStream err;
    private final IdentityPublicKey broker;
    private final Set<DrasylAddress> superPeers = new HashSet<>();
    private final ConsumerTaskRecord taskRecord;
    private String token;
    private DrasylChannel brokerChannel;
    private IdentityPublicKey provider;
    private DrasylChannel providerChannel;
    private final String source;
    private final Object[] input;
    private ScheduledFuture<?> timeoutGuard;

    public ConsumerHandler(final PrintStream out,
                           final PrintStream err,
                           final DrasylAddress address,
                           final IdentityPublicKey broker,
                           final String source,
                           final Object[] input) {
        this.out = requireNonNull(out);
        this.err = requireNonNull(err);
        this.broker = requireNonNull(broker);
        this.source = requireNonNull(source);
        this.input = requireNonNull(input);
        this.taskRecord = new ConsumerTaskRecord(address, broker, source, input);
        logger = new CsvLogger("consumer-" + address.toString().substring(0, 8) + ".csv");
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        ctx.fireChannelActive();
        LOG.info("Start Consumer {}.", ctx.channel().localAddress());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        logger.log(taskRecord);
        ctx.fireChannelInactive();
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
                LOG.info("Consumer online!");
                state = State.ONLINE;

                LOG.info("Connect to Broker {}.", broker);
                state = BROKER_CONNECTION_ISSUED;
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
            if (evt instanceof ConnectionEstablished) {
                LOG.info("Connection to Broker {} established.", broker);
                state = BROKER_CONNECTION_ESTABLISHED;
                requestResource(ctx);
            }
            else if (evt instanceof ConnectionFailed) {
                LOG.info("Failed to connect to Broker {}. Shutdown Consumer.", broker, ((ConnectionFailed) evt).cause());
                state = CLOSED;
                ctx.pipeline().close();
            }
            else if (evt instanceof ConnectionClosed) {
                LOG.info("Broker {} closed connection. Shutdown Consumer.", broker);
                state = CLOSED;
                ctx.pipeline().close();
            }
        }
        else if (sender.equals(provider)) {
            if (evt instanceof ConnectionEstablished) {
                LOG.info("Connection to Provider {} established.", broker);
                state = PROVIDER_CONNECTION_ESTABLISHED;
                offloadTask(ctx);
            }
            else if (evt instanceof ConnectionFailed) {
                LOG.info("Failed to connect to Provider {}. Shutdown Consumer.", broker, ((ConnectionFailed) evt).cause());
                state = CLOSED;
                ctx.pipeline().close();
            }
            else if (state == TASK_OFFLOADED && evt instanceof ConnectionClosed) {
                LOG.info("Provider {} closed connection. Shutdown Consumer.", broker);
                // inform broker
                brokerChannel.writeAndFlush(new TaskFailed(token)).addListener((ChannelFutureListener) future2 -> {
                    state = CLOSED;
                    ctx.pipeline().close();
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
                LOG.info("Got resource response {} from Broker {}.", msg, sender);
                timeoutGuard.cancel(false);
                token = ((ResourceResponse) msg).getToken();
                provider = ((ResourceResponse) msg).getPublicKey();
                taskRecord.resourceResponded(provider, token);
                if (provider == null) {
                    LOG.info("Broker has not found any resource for us. Shutdown Consumer.");
                    err.println("No resource for offloading available. Please try again later.");
                    state = CLOSED;
                    ctx.pipeline().close();
                    return;
                }

                LOG.info("Connect to Provider {}.", provider);
                state = PROVIDER_CONNECTION_ISSUED;
                providerChannel = new DrasylChannel((DrasylServerChannel) ctx.channel(), provider);
                ctx.pipeline().fireChannelRead(providerChannel);
            }
        }
        else if (sender.equals(provider)) {
            if (state == TASK_OFFLOADED && msg instanceof ReturnResult) {
                LOG.info("Got result {} from Provider {}.", msg, sender);
                timeoutGuard.cancel(false);
                taskRecord.resultReturned(((ReturnResult) msg).getOutput(), ((ReturnResult) msg).getExecutionTime());

                out.println("Output : " + Arrays.toString(((ReturnResult) msg).getOutput()));

                // inform broker
                state = CLOSED;
                brokerChannel.writeAndFlush(new TaskResultReceived(token)).addListener((ChannelFutureListener) future -> ctx.pipeline().close());
            }
        }
    }

    private void requestResource(final ChannelHandlerContext ctx) {
        LOG.info("Request resource at Broker {}.", broker);
        state = RESOURCE_REQUESTED;
        final ResourceRequest request = new ResourceRequest();
        taskRecord.resourceRequest();
        brokerChannel.writeAndFlush(request).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                LOG.info("Request {} at Broker {} arrived!", request, broker);
                taskRecord.resourceRequested();
            }
            else {
                LOG.info("Failed to sent request {} to Broker {}. Shutdown Consumer.", request, broker, future.cause());
                state = CLOSED;
                ctx.pipeline().close();
            }
        });

        // apply timeout guard
        timeoutGuard = ctx.executor().schedule(() -> {
            LOG.info("Broker {} has not responded to our request {} within {}ms. Shutdown Consumer.", broker, request, RESOURCE_REQUEST_TIMEOUT);
            state = CLOSED;
            ctx.pipeline().close();
        }, RESOURCE_REQUEST_TIMEOUT, MILLISECONDS);
    }

    private void offloadTask(final ChannelHandlerContext ctx) {
        final OffloadTask msg = new OffloadTask(token, source, input);
        state = TASK_OFFLOADED;
        LOG.info("Offload task {} to Provider {}.", msg, provider);
        taskRecord.offloadTask();
        providerChannel.writeAndFlush(msg).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                LOG.info("Task arrived at Provider {}!", provider);
                taskRecord.offloadedTask();

                // inform broker
                brokerChannel.writeAndFlush(new TaskOffloaded(token)).addListener(FIRE_EXCEPTION_ON_FAILURE);
            }
            else {
                LOG.info("Failed to offload task {} to Provider {}. Shutdown Consumer.", msg, provider, future.cause());

                // inform broker
                brokerChannel.writeAndFlush(new TaskFailed(token)).addListener((ChannelFutureListener) future2 -> {
                    state = CLOSED;
                    ctx.pipeline().close();
                });
            }
        });

        // apply timeout guard
        timeoutGuard = ctx.executor().schedule(() -> {
            LOG.info("Provider {} has not provided results for our task {} within {}ms. Shutdown Consumer.", provider, msg, OFFLOAD_TASK_TIMEOUT);

            // inform broker
            brokerChannel.writeAndFlush(new TaskFailed(token)).addListener((ChannelFutureListener) future -> {
                state = CLOSED;
                ctx.pipeline().close();
            });
        }, OFFLOAD_TASK_TIMEOUT, MILLISECONDS);
    }

    enum State {
        STARTED,
        ONLINE,
        BROKER_CONNECTION_ISSUED,
        BROKER_CONNECTION_ESTABLISHED,
        RESOURCE_REQUESTED,
        PROVIDER_CONNECTION_ISSUED,
        PROVIDER_CONNECTION_ESTABLISHED,
        TASK_OFFLOADED,
        CLOSED
    }
}
