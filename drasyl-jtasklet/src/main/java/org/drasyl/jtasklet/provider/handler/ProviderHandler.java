package org.drasyl.jtasklet.provider.handler;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.drasyl.channel.DrasylChannel;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.handler.PeersRttReport;
import org.drasyl.handler.discovery.AddPathAndSuperPeerEvent;
import org.drasyl.handler.discovery.RemoveSuperPeerAndPathEvent;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.jtasklet.broker.ResourceProvider;
import org.drasyl.jtasklet.event.ConnectionClosed;
import org.drasyl.jtasklet.event.ConnectionEstablished;
import org.drasyl.jtasklet.event.ConnectionEvent;
import org.drasyl.jtasklet.event.ConnectionFailed;
import org.drasyl.jtasklet.event.MessageReceived;
import org.drasyl.jtasklet.event.NodeOffline;
import org.drasyl.jtasklet.event.NodeOnline;
import org.drasyl.jtasklet.event.TaskletEvent;
import org.drasyl.jtasklet.message.OffloadTask;
import org.drasyl.jtasklet.message.ProviderReset;
import org.drasyl.jtasklet.message.RegisterProvider;
import org.drasyl.jtasklet.message.ReturnResult;
import org.drasyl.jtasklet.message.TaskExecuted;
import org.drasyl.jtasklet.message.TaskExecuting;
import org.drasyl.jtasklet.message.TaskletMessage;
import org.drasyl.jtasklet.provider.runtime.ExecutionResult;
import org.drasyl.jtasklet.provider.runtime.RuntimeEnvironment;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.PrintStream;
import java.util.HashSet;
import java.util.Set;

import static java.util.Objects.requireNonNull;
import static org.drasyl.jtasklet.provider.handler.ProviderHandler.State.BROKER_CONNECTION_ESTABLISHED;
import static org.drasyl.jtasklet.provider.handler.ProviderHandler.State.BROKER_CONNECTION_ISSUED;
import static org.drasyl.jtasklet.provider.handler.ProviderHandler.State.BROKER_REGISTERED;
import static org.drasyl.jtasklet.provider.handler.ProviderHandler.State.CLOSED;
import static org.drasyl.jtasklet.provider.handler.ProviderHandler.State.EXECUTE_TASK;
import static org.drasyl.jtasklet.provider.handler.ProviderHandler.State.ONLINE;

public class ProviderHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(ProviderHandler.class);
    private State state = State.STARTED;
    private final PrintStream out;
    private final PrintStream err;
    private final Set<DrasylAddress> superPeers = new HashSet<>();
    private final DrasylAddress broker;
    private final long benchmark;
    private DrasylChannel brokerChannel;
    private PeersRttReport rttReport;
    private String token;
    private final EventLoopGroup eventLoop = new NioEventLoopGroup(1);
    private final RuntimeEnvironment runtimeEnvironment;
    private DrasylChannel consumerChannel;
    private DrasylAddress consumer;

    public ProviderHandler(final PrintStream out,
                           final PrintStream err,
                           final DrasylAddress broker,
                           final long benchmark,
                           RuntimeEnvironment runtimeEnvironment) {
        this.out = requireNonNull(out);
        this.err = requireNonNull(err);
        this.broker = requireNonNull(broker);
        this.benchmark = benchmark;
        this.runtimeEnvironment = requireNonNull(runtimeEnvironment);
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        ctx.fireChannelActive();
        LOG.info("Start Provider {}.", ctx.channel().localAddress());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        eventLoop.shutdownGracefully();
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
        else if (evt instanceof PeersRttReport) {
            rttReport = (PeersRttReport) evt;
        }
        else if (evt instanceof TaskletEvent) {
            if (evt instanceof NodeOnline) {
                LOG.info("Provider online!");
                state = ONLINE;
                LOG.info("Connect to Broker {}.", broker);
                state = BROKER_CONNECTION_ISSUED;
                brokerChannel = new DrasylChannel((DrasylServerChannel) ctx.channel(), broker);
                ctx.pipeline().fireChannelRead(brokerChannel);
            }
            else if (evt instanceof ConnectionEvent) {
                connectionChanged(ctx, (ConnectionEvent) evt);
            }
            else if (evt instanceof MessageReceived) {
                messageReceived(((MessageReceived<?>) evt).channel(), ((MessageReceived<?>) evt).msg());
            }
        }

        ctx.fireUserEventTriggered(evt);
    }

    private void connectionChanged(final ChannelHandlerContext ctx, final ConnectionEvent evt) {
        final DrasylAddress sender = evt.sender();

        if (evt instanceof ConnectionEstablished && sender.equals(broker)) {
            LOG.info("Connection to Broker {} established.", broker);
            state = BROKER_CONNECTION_ESTABLISHED;
            registerAtBroker();
        }
        else if (evt instanceof ConnectionFailed && sender.equals(broker)) {
            LOG.info("Failed to connect to Broker {}.", broker, ((ConnectionFailed) evt).cause());
            state = CLOSED;
            ctx.pipeline().close();
        }
        else if (evt instanceof ConnectionClosed && sender.equals(broker)) {
            LOG.info("Broker {} closed connection. Shutdown Provider.", broker);
            state = CLOSED;
            ctx.pipeline().close();
        }
        // beside the broker, only consumers will contact us
        else if (evt instanceof ConnectionEstablished) {
            LOG.info("Connection to Consumer {} established.", sender);
        }
    }

    private void messageReceived(final DrasylChannel channel,
                                 final TaskletMessage msg) {
        final DrasylAddress sender = (DrasylAddress) channel.remoteAddress();

        if (msg instanceof OffloadTask) {
            LOG.info("Got task {} from Consumer {}. Schedule it.", msg, sender);
            consumer = sender;
            consumerChannel = channel;
            token = ((OffloadTask) msg).getToken();

            state = EXECUTE_TASK;

            // inform broker
            brokerChannel.writeAndFlush(new TaskExecuting(token));

            eventLoop.execute(() -> {
                LOG.info("Start executing of task {} from Consumer {}.", msg, sender);
                final ExecutionResult result = runtimeEnvironment.execute(((OffloadTask) msg).getSource(), ((OffloadTask) msg).getInput());
                LOG.info("Execution of task {} from Consumer {} finished in {}ms.", msg, sender, result.getExecutionTime());

                final ReturnResult response = new ReturnResult(result.getOutput(), result.getExecutionTime());
                LOG.info("Send result {} back to Consumer {}.", response, sender);
                consumerChannel.writeAndFlush(response).addListener((ChannelFutureListener) future -> {
                    if (future.isSuccess()) {
                        // inform broker
                        brokerChannel.writeAndFlush(new TaskExecuted(token, ResourceProvider.randomToken()));

                        LOG.info("Result arrived at Consumer {}! Close connection to Consumer.", sender);
                        future.channel().close();

                    }
                    else {
                        // inform broker
                        brokerChannel.writeAndFlush(new ProviderReset(token));

                        future.channel().pipeline().fireExceptionCaught(future.cause());
                    }

                    state = BROKER_REGISTERED;
                    LOG.info("Send me tasks! I'm hungry!");
                });
            });
        }
    }

    private void registerAtBroker() {
        token = ResourceProvider.randomToken();
        final RegisterProvider msg = new RegisterProvider(benchmark, token);
        LOG.info("Register {} at Broker {}.", msg, broker);
        brokerChannel.writeAndFlush(msg).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                LOG.info("Registration at Broker {} arrived!", broker);
                state = BROKER_REGISTERED;
                LOG.info("Send me tasks! I'm hungry!");
            }
            else {
                future.channel().pipeline().fireExceptionCaught(future.cause());
            }
        });
    }

    enum State {
        STARTED,
        ONLINE,
        BROKER_CONNECTION_ISSUED,
        BROKER_CONNECTION_ESTABLISHED,
        BROKER_REGISTERED,
        EXECUTE_TASK,
        CLOSED
    }
}
