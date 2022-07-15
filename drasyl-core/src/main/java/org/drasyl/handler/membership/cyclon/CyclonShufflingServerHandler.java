package org.drasyl.handler.membership.cyclon;

import io.netty.channel.AddressedEnvelope;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.drasyl.channel.OverlayAddressedMessage;
import org.drasyl.identity.DrasylAddress;

import java.net.SocketAddress;
import java.util.HashSet;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * Handles "Enhanced Shuffling" requests initiated by {@link CyclonShufflingClientHandler}.
 */
public class CyclonShufflingServerHandler extends SimpleChannelInboundHandler<OverlayAddressedMessage<CyclonShuffleRequest>> {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(CyclonShufflingServerHandler.class);
    private final int shuffleSize;
    private final CyclonView view;

    /**
     * @param shuffleSize max. number of neighbors to shuffle (denoted as <i>ℓ</i> in the paper)
     * @param view
     */
    public CyclonShufflingServerHandler(final int shuffleSize,
                                        final CyclonView view) {
        if (shuffleSize < 1 || shuffleSize > view.viewSize()) {
            throw new IllegalArgumentException("shuffleSize (ℓ) must be within the interval [1, c].");
        }
        this.shuffleSize = shuffleSize;
        logger.debug("shuffleSize (ℓ) = {}", this.shuffleSize);
        this.view = requireNonNull(view);
    }

    /*
     * Channel Events
     */

    @SuppressWarnings("unchecked")
    @Override
    public boolean acceptInboundMessage(final Object msg) {
        return msg instanceof AddressedEnvelope &&
                ((AddressedEnvelope<?, SocketAddress>) msg).content() instanceof CyclonShuffleRequest;
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx,
                                final OverlayAddressedMessage<CyclonShuffleRequest> msg) {
        handleShuffleRequest(ctx, msg);
    }

    /*
     * Shuffle
     */

    private void handleShuffleRequest(final ChannelHandlerContext ctx,
                                      final OverlayAddressedMessage<CyclonShuffleRequest> request) {
        logger.debug("Received following shuffle request from `{}`:\n{}", request.sender(), request.content());
        logger.trace("Current neighbors: {}", view);

        // […] the receiving node Q replies by sending back a random subset of at most ℓ of its
        // neighbors, …
        final Set<CyclonNeighbor> randomNeighbors = view.randomNeighbors(shuffleSize);
        logger.trace("Random neighbors: {}", randomNeighbors);

        // Remove P (in paper at step 6, but doing so here to remove useless transport overload)
        randomNeighbors.remove(CyclonNeighbor.of((DrasylAddress) request.sender()));

        final OverlayAddressedMessage<CyclonShuffleResponse> response = new OverlayAddressedMessage<>(CyclonShuffleResponse.of(randomNeighbors), request.sender(), null);
        logger.debug("Send following shuffle response to `{}`:\n{}", response.recipient(), response.content());
        ctx.writeAndFlush(response).addListener((ChannelFutureListener) future -> {
            if (future.cause() != null) {
                logger.warn("Unable to send the following shuffle response to `{}`:\n{}", response.recipient(), response.content(), future.cause());
            }
        });

        // … and updates its own cache to accommodate all received entries (executes steps 6 and 7
        // to update its own cache accordingly).
        final Set<CyclonNeighbor> receivedNeighbors = new HashSet<>(request.content().getNeighbors());

        // 6. Discard entries pointing at Q, and entries already contained in Q's cache.
        receivedNeighbors.remove(CyclonNeighbor.of((DrasylAddress) ctx.channel().localAddress()));
        receivedNeighbors.removeAll(view.getNeighbors());

        // 7. Update Q’s cache to include all remaining entries, by firstly using empty cache slots
        // (if any), and secondly replacing entries among the ones sent to P.
        view.update(receivedNeighbors, randomNeighbors);
        logger.debug("Successfully merged! New view is:\n{}", this.view);
    }
}
