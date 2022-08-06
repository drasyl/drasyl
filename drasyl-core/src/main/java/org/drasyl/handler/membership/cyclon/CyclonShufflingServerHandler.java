/*
 * Copyright (c) 2020-2022 Heiko Bornholdt and Kevin Röbert
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.drasyl.handler.membership.cyclon;

import io.netty.channel.AddressedEnvelope;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.drasyl.channel.OverlayAddressedMessage;
import org.drasyl.identity.DrasylAddress;

import java.net.SocketAddress;
import java.util.HashSet;
import java.util.Set;

import static java.util.Objects.requireNonNull;
import static org.drasyl.util.Preconditions.requirePositive;

/**
 * Waits for {@link CyclonShuffleRequest}s sent by {@link CyclonShufflingClientHandler}.
 * <p>
 * This handler should be used together with {@link CyclonShufflingClientHandler} and
 * {@link CyclonCodec}.
 * <blockquote>
 * <pre>
 *  {@link ChannelPipeline} p = ...;
 *  {@link CyclonView} view = {@link CyclonView}.ofKeys(8, Set.of(pubKeyA, pubKeyB, ...));
 *  ...
 *  p.addLast("cyclon_codec", <b>new {@link CyclonCodec}()</b>);
 *  p.addLast("cyclon_client", <b>new {@link CyclonShufflingClientHandler}(4, 10_000, view)</b>);
 *  p.addLast("cyclon_server", <b>new {@link CyclonShufflingServerHandler}(4, view)</b>);
 *  ...
 *  </pre>
 * </blockquote>
 *
 * @see CyclonShufflingClientHandler
 * @see <a href="https://doi.org/10.1007/s10922-005-4441-x">CYCLON: Inexpensive Membership
 * Management for Unstructured P2P Overlays</a>
 */
public class CyclonShufflingServerHandler extends SimpleChannelInboundHandler<OverlayAddressedMessage<CyclonShuffleRequest>> {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(CyclonShufflingServerHandler.class);
    private final int shuffleSize;
    private final CyclonView view;

    /**
     * @param shuffleSize max. number of neighbors to shuffle (denoted as <i>ℓ</i> in the paper)
     * @param view        local peer's (partial) view of the network
     */
    public CyclonShufflingServerHandler(final int shuffleSize,
                                        final CyclonView view) {
        this.shuffleSize = requirePositive(shuffleSize);
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
        randomNeighbors.remove(CyclonNeighbor.of(request.sender()));

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
