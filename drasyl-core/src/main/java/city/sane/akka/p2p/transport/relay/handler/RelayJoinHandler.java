package city.sane.akka.p2p.transport.relay.handler;

import org.drasyl.core.common.messages.Join;
import org.drasyl.core.common.messages.Message;
import org.drasyl.core.common.messages.RelayException;
import org.drasyl.core.common.messages.Welcome;
import org.drasyl.core.common.models.SessionChannel;
import org.drasyl.core.common.models.SessionUID;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/**
 * This handler sends a {@link Join} to the relay once connection is established and waits for confirmation. The
 * confirmation is indicated by an ({@link Welcome}) from the relay.<br>
 * If the confirmation response does not arrive within a certain period of time, the join request is assumed to have
 * failed.
 */
public class RelayJoinHandler extends SimpleChannelInboundHandler<Message> implements Function<CompletableFuture<Void>, RelayJoinHandler> {
    private static final Logger LOG = LoggerFactory.getLogger(RelayJoinHandler.class);

    private final Duration timeout;
    private final Request<Join> joinRequest;
    private ChannelPromise joinFuture;
    private ScheduledFuture<?> timeoutSchedule;
    private CompletableFuture<Void> channelReadyFuture;

    public RelayJoinHandler(String systemName, String channel, Duration timeout) {
        this(
                new Request<>(new Join(SessionUID.of(requireNonNull(systemName)),
                        Set.of(SessionChannel.of(requireNonNull(channel))))),
                timeout, CompletableFuture.completedFuture(null)
        );
    }

    RelayJoinHandler(Request<Join> joinRequest, Duration timeout, CompletableFuture<Void> channelReadyFuture) {
        this.joinRequest = requireNonNull(joinRequest);
        this.timeout = requireNonNull(timeout);
        this.channelReadyFuture = channelReadyFuture;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        joinFuture = ctx.newPromise();
        initiateJoin(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message message) {
        ctx.fireChannelRead(message);
    }

    public ChannelPromise joinFuture() {
        return joinFuture;
    }

    private void initiateJoin(ChannelHandlerContext ctx) {
        channelReadyFuture.whenComplete((fut, error) -> {
            if (error == null) {
                LOG.debug("Send join request to relay.");
                ctx.writeAndFlush(joinRequest);
                joinRequest.getResponse().thenAccept(message -> {
                    if (message instanceof Welcome) {
                        confirmJoin(ctx);
                    } else if (message instanceof RelayException) {
                        failJoin(new RelayJoinException(((RelayException) message).getException()));
                    }
                });
            }
        });
        timeoutSchedule = ctx.executor().schedule(new RelayJoinTimeoutTask(this, ctx), timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void confirmJoin(ChannelHandlerContext ctx) {
        LOG.debug("Join confirmation received from relay. Remove this handler from pipeline.");
        joinFuture.setSuccess();
        timeoutSchedule.cancel(false);
        ctx.pipeline().remove(this);
    }

    private void failJoin(RelayJoinException cause) {
        if (!joinFuture.isDone()) {
            LOG.debug("Join failed: {}", cause.getMessage());
            joinFuture.setFailure(cause);
        }
    }

    @Override
    public RelayJoinHandler apply(CompletableFuture<Void> handshakeFuture) {
        return new RelayJoinHandler(joinRequest, timeout, handshakeFuture);
    }

    public static class RelayJoinException extends ChannelException {
        RelayJoinException(String message) {
            super(message);
        }
    }

    public static class RelayJoinTimeoutTask implements Runnable {
        private RelayJoinHandler relayJoinHandler;
        private final ChannelHandlerContext ctx;

        RelayJoinTimeoutTask(RelayJoinHandler relayJoinHandler, ChannelHandlerContext ctx) {
            this.relayJoinHandler = relayJoinHandler;
            this.ctx = ctx;
        }

        @Override
        public void run() {
            if (!ctx.channel().isOpen()) {
                return;
            }

            relayJoinHandler.failJoin(new RelayJoinException("Timeout"));
        }
    }
}
