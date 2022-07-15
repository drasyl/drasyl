package org.drasyl.handler.membership.cyclon;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.drasyl.channel.OverlayAddressedMessage;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.Pair;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.util.Preconditions.requirePositive;

/**
 * Periodically initiates CYCLON shuffle.
 */
public class CyclonShufflingClientHandler extends SimpleChannelInboundHandler<OverlayAddressedMessage<Object>> {
    private static final Logger LOG = LoggerFactory.getLogger(CyclonShufflingClientHandler.class);
    private CyclonView view;
    private final long shuffleInterval;
    private final int shuffleSize;
    private DrasylAddress requestee;

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
        LOG.trace("Start Shuffling...");

        // previous shuffle still running? -> shuffle timeout
        if (requestee != null) {
            LOG.debug("Shuffle request timed out.");
            requestee = null;
        }

        if (view.isEmpty()) {
            LOG.debug("My view is empty. Nothing to do!");
            return;
        }

        LOG.trace("Current neighbors: {}", view);

        // 1. Increase by one the age of all neighbors.
        LOG.trace("Increase by one the age of all neighbors.");
        view = view.increaseAge();

        // 2. Select neighbor Q with the highest age among all neighbors, and ℓ − 1 other random neighbors.
        LOG.trace("Select neighbor Q with the highest age among all neighbors, and ℓ − 1 other random neighbors.");
        final Pair<CyclonNeighbor, Set<CyclonNeighbor>> result = view.highestAgeAndOtherRandomNeighbors(shuffleSize - 1);
        final CyclonNeighbor q = result.first();
        final Set<CyclonNeighbor> otherRandomNeighbors = result.second();
        LOG.trace("Q = {}; other random neighbors = {}", q, otherRandomNeighbors);
        final Set<CyclonNeighbor> neighborsSubset = new HashSet<>(otherRandomNeighbors);

        // 3. Replace Q’s entry with a new entry of age 0 and with P’s address.
        LOG.trace("Replace Q’s entry with a new entry of age 0 and with P’s address.");
        neighborsSubset.remove(q);
        neighborsSubset.add(CyclonNeighbor.of((DrasylAddress) ctx.channel().localAddress()));
        LOG.trace("updated subset = {}", neighborsSubset);

        // 4. Send the updated subset to peer Q.
        LOG.trace("Send the updated subset to peer Q.");
        final OverlayAddressedMessage<CyclonShuffleRequest> shuffleRequest = new OverlayAddressedMessage<>(CyclonShuffleRequest.of(neighborsSubset), q.getAddress(), null);
        LOG.debug("Send following shuffle request to `{}`:\n{}", shuffleRequest.recipient(), shuffleRequest.content());
        ctx.writeAndFlush(shuffleRequest).addListener((ChannelFutureListener) future -> {
            if (future.cause() != null) {
                LOG.warn("Unable to send the following shuffle request to `{}`:\n{}", shuffleRequest.recipient(), shuffleRequest.content(), future.cause());
            }
        });
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
        System.out.println();
    }

    public CyclonView getView() {
        return view;
    }

    public void setView(final CyclonView view) {
        this.view = view;
    }
}
