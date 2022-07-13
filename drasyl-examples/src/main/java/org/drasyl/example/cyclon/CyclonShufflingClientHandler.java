package org.drasyl.example.cyclon;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.drasyl.channel.OverlayAddressedMessage;
import org.drasyl.handler.membership.cyclon.CyclonView;
import org.drasyl.identity.DrasylAddress;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.util.Preconditions.requirePositive;

/**
 * Periodically initiates CYCLON shuffle.
 */
public class CyclonShufflingClientHandler extends SimpleChannelInboundHandler<OverlayAddressedMessage<Object>> {
    private final CyclonView view;
    private final long shuffleInterval;
    private final int shuffleSize;
    private final DrasylAddress requestee;

    CyclonShufflingClientHandler(final CyclonView view,
                                 final long shuffleInterval,
                                 final int shuffleSize,
                                 final DrasylAddress requestee) {
        this.view = requireNonNull(view);
        this.shuffleInterval = requirePositive(shuffleInterval);
        this.shuffleSize = requirePositive(shuffleSize);
        this.requestee = requestee;
    }

    public CyclonShufflingClientHandler(final CyclonView initialView,
                                        final long shuffleInterval,
                                        final int shuffleSize) {
        this(initialView, shuffleInterval, shuffleSize, null);
    }

    /*
     * Shuffling Initiation
     */

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        if (ctx.channel().isActive()) {
            scheduleShuffling(ctx);
        }
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        scheduleShuffling(ctx);
        ctx.fireChannelActive();
    }

    private void scheduleShuffling(final ChannelHandlerContext ctx) {
        ctx.executor().scheduleWithFixedDelay(() -> initiateShuffling(ctx), shuffleInterval, shuffleInterval, MILLISECONDS);
    }

    private void initiateShuffling(final ChannelHandlerContext ctx) {

    }

    /*
     * Shuffling Response Handling
     */

    @Override
    public boolean acceptInboundMessage(final Object message) {
        return requestee != null && message instanceof OverlayAddressedMessage && ((OverlayAddressedMessage<?>) message).content() instanceof Object && ((OverlayAddressedMessage<Object>) message).sender().equals(requestee);
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx,
                                final OverlayAddressedMessage<Object> msg) throws Exception {

    }
}
