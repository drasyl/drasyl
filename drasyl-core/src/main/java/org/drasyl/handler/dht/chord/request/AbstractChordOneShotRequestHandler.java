package org.drasyl.handler.dht.chord.request;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.ScheduledFuture;
import org.drasyl.channel.OverlayAddressedMessage;
import org.drasyl.handler.dht.chord.message.ChordMessage;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.logging.Logger;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Implemented by handlers that need to sent a request to a peer and waiting for the response
 *
 * @param <T> response message type
 * @param <R> future result type
 */
abstract class AbstractChordOneShotRequestHandler<T extends ChordMessage, R> extends SimpleChannelInboundHandler<OverlayAddressedMessage<T>> {
    private final ChordMessage request;
    protected final IdentityPublicKey peer;
    private final Promise<R> promise;
    private ScheduledFuture<?> timeoutGuard;

    protected AbstractChordOneShotRequestHandler(final ChordMessage request,
                                                 final IdentityPublicKey peer,
                                                 final Promise<R> promise) {
        this.request = requireNonNull(request);
        this.peer = requireNonNull(peer);
        this.promise = requireNonNull(promise);
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        logger().debug("Send request `{}` to peer `{}`.", request, peer);
        ctx.writeAndFlush(new OverlayAddressedMessage<>(request, peer)).addListener((ChannelFutureListener) future -> {
            if (future.cause() == null) {
                timeoutGuard = ctx.executor().schedule(() -> {
                    //failRequest(ctx, new Exception(StringUtil.simpleClassName(AbstractChordOneShotRequestHandler.this) + " timeout after 5000ms."));
                    promise.trySuccess(null);
                    ctx.pipeline().remove(ctx.name());
                }, 5000, MILLISECONDS);
            }
            else {
                failRequest(ctx, future.cause());
            }
        });
    }

    @Override
    public void handlerRemoved(final ChannelHandlerContext ctx) {
        cancelTimeoutGuard();
        promise.cancel(false);
    }

    @Override
    public boolean acceptInboundMessage(final Object msg) throws Exception {
        return msg instanceof OverlayAddressedMessage && peer.equals(((OverlayAddressedMessage<?>) msg).sender()) && acceptResponse(((OverlayAddressedMessage<?>) msg).content());
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx,
                                final OverlayAddressedMessage<T> msg) throws Exception {
        cancelTimeoutGuard();
        final T response = msg.content();
        logger().debug("Go response for request `{}` to peer `{}`: {}", request, peer, response);
        handleResponse(ctx, response, promise);
        ctx.pipeline().remove(ctx.name());
    }

    protected abstract boolean acceptResponse(final Object msg);

    private void cancelTimeoutGuard() {
        if (timeoutGuard != null) {
            timeoutGuard.cancel(false);
            timeoutGuard = null;
        }
    }

    private void failRequest(final ChannelHandlerContext ctx, final Throwable cause) {
        logger().debug("Request `{}` to peer `{}` failed:", request, peer, cause);
        cancelTimeoutGuard();
        promise.tryFailure(cause);
        ctx.pipeline().remove(ctx.name());
    }

    protected abstract void handleResponse(final ChannelHandlerContext ctx,
                                           final T response,
                                           final Promise<R> promise);

    protected abstract Logger logger();
}
