package org.drasyl.handler.membership.cyclon;

import io.netty.channel.AddressedEnvelope;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.drasyl.channel.OverlayAddressedMessage;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.Pair;

import java.net.SocketAddress;
import java.util.HashSet;
import java.util.Set;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Initiates the "Enhanced Shuffling" algorithm of CYCLON.
 */
public class CyclonShufflingClientHandler extends SimpleChannelInboundHandler<AddressedEnvelope<ShuffleResponse, SocketAddress>> {
    public static final int VIEW_SIZE = 8;
    public static final int SHUFFLE_SIZE = 4;
    public static final int SHUFFLE_INTERVAL = 10_000;
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(CyclonShufflingClientHandler.class);
    private final int shuffleSize;
    private final int shuffleInterval;
    private final CyclonView view;
    private OverlayAddressedMessage<ShuffleRequest> shuffleRequest;

    /**
     * @param shuffleSize     max. number of neighbors to shuffle (denoted as <i>ℓ</i> in the
     *                        paper)
     * @param shuffleInterval period for shuffle requests (denoted as <i>ΔT</i> in the paper)
     * @param view
     * @param shuffleRequest
     */
    public CyclonShufflingClientHandler(final int shuffleSize,
                                        final int shuffleInterval,
                                        final CyclonView view,
                                        final OverlayAddressedMessage<ShuffleRequest> shuffleRequest) {
        if (shuffleSize < 1 || shuffleSize > view.viewSize()) {
            throw new IllegalArgumentException("shuffleSize (ℓ) must be within the interval [1, c].");
        }
        this.shuffleSize = shuffleSize;
        this.shuffleInterval = shuffleInterval;
        logger.debug("shuffleSize (ℓ) = {}", this.shuffleSize);
        logger.debug("shuffleInterval (ΔT) = {}ms", this.shuffleInterval);
        this.view = requireNonNull(view);
        this.shuffleRequest = shuffleRequest;
    }

    /*
     * Channel Events
     */

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        if (ctx.channel().isActive()) {
            ctx.executor().scheduleWithFixedDelay(() -> initiateShuffle(ctx), (long) (shuffleInterval * Math.random()), shuffleInterval, MILLISECONDS);
        }
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        ctx.executor().scheduleWithFixedDelay(() -> initiateShuffle(ctx), (long) (shuffleInterval * Math.random()), shuffleInterval, MILLISECONDS);
        ctx.fireChannelActive();
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean acceptInboundMessage(final Object msg) {
        return msg instanceof AddressedEnvelope &&
                ((AddressedEnvelope<?, SocketAddress>) msg).content() instanceof ShuffleResponse &&
                shuffleRequest != null && ((AddressedEnvelope<?, SocketAddress>) msg).sender().equals(shuffleRequest.recipient());
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx,
                                final AddressedEnvelope<ShuffleResponse, SocketAddress> msg) {
        handleShuffleResponse(ctx, msg);
    }

    /*
     * Shuffle
     */

    @SuppressWarnings({ "java:S3655" })
    private void initiateShuffle(final ChannelHandlerContext ctx) {
        logger.trace("Start Shuffling...");

        // previous shuffle still running? -> shuffle timeout
        if (shuffleRequest != null) {
            logger.debug("Shuffle request timed out.");
            shuffleRequest = null;
        }

        if (view.isEmpty()) {
            logger.debug("My view is empty. Nothing to do!");
            return;
        }

        logger.trace("Current neighbors: {}", view);

        // 1. Increase by one the age of all neighbors.
        logger.trace("Increase by one the age of all neighbors.");
        view.increaseAge();

        // 2. Select neighbor Q with the highest age among all neighbors, and ℓ − 1 other random neighbors.
        logger.trace("Select neighbor Q with the highest age among all neighbors, and ℓ − 1 other random neighbors.");
        final Pair<CyclonNeighbor, Set<CyclonNeighbor>> result = view.highestAgeAndOtherRandomNeighbors(shuffleSize - 1);
        final CyclonNeighbor q = result.first();
        final Set<CyclonNeighbor> otherRandomNeighbors = result.second();
        logger.trace("Q = {}; other random neighbors = {}", q, otherRandomNeighbors);
        final Set<CyclonNeighbor> neighborsSubset = new HashSet<>(otherRandomNeighbors);

        // 3. Replace Q’s entry with a new entry of age 0 and with P’s address.
        logger.trace("Replace Q’s entry with a new entry of age 0 and with P’s address.");
        view.remove(q);
        neighborsSubset.add(CyclonNeighbor.of((DrasylAddress) ctx.channel().localAddress()));
        logger.trace("updated subset = {}", neighborsSubset);

        // 4. Send the updated subset to peer Q.
        logger.trace("Send the updated subset to peer Q.");
        shuffleRequest = new OverlayAddressedMessage<>(ShuffleRequest.of(neighborsSubset), q.getAddress(), null);
        logger.debug("Send following shuffle request to `{}`:\n{}", shuffleRequest.recipient(), shuffleRequest.content());
        ctx.writeAndFlush(shuffleRequest).addListener((ChannelFutureListener) future -> {
            if (future.cause() != null) {
                logger.warn("Unable to send the following shuffle request to `{}`:\n{}", shuffleRequest.recipient(), shuffleRequest.content(), future.cause());
            }
        });
    }

    private void handleShuffleResponse(final ChannelHandlerContext ctx,
                                       final AddressedEnvelope<ShuffleResponse, SocketAddress> msg) {
        logger.debug("Received following shuffle response from `{}`:\n{}", msg.sender(), msg.content());
        logger.trace("Current neighbors: {}", view);

        // 5. Receive from Q a subset of no more than i of its own entries.
        final Set<CyclonNeighbor> receivedNeighbors = new HashSet<>(msg.content().getNeighbors());

        // 6. Discard entries pointing at P, and entries already contained in P's cache.
        receivedNeighbors.remove(CyclonNeighbor.of((DrasylAddress) ctx.channel().localAddress()));
        receivedNeighbors.removeAll(view.getNeighbors());

        // 7. Update P’s cache to include all remaining entries, by firstly using empty cache slots
        // (if any), and secondly replacing entries among the ones sent to Q.
        view.update(receivedNeighbors, shuffleRequest.content().getNeighbors());

        logger.debug("Successfully merged! New view is:\n{}", this.view);
        shuffleRequest = null;
    }
}
