package org.drasyl.example.cyclon;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.drasyl.channel.OverlayAddressedMessage;
import org.drasyl.handler.membership.cyclon.CyclonView;

import static java.util.Objects.requireNonNull;
import static org.drasyl.util.Preconditions.requirePositive;

/**
 * Waits for CYCLON shuffle requests.
 */
public class CyclonShufflingServerHandler extends SimpleChannelInboundHandler<OverlayAddressedMessage<Object>> {
    private final CyclonView view;
    private final int shuffleSize;

    public CyclonShufflingServerHandler(final CyclonView initialView,
                                        final int shuffleSize) {
        this.view = requireNonNull(initialView);
        this.shuffleSize = requirePositive(shuffleSize);
    }

    /*
     * Shuffling Request Handling
     */

    @Override
    public boolean acceptInboundMessage(final Object message) {
        return message instanceof OverlayAddressedMessage && ((OverlayAddressedMessage<?>) message).content() instanceof Object;
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx,
                                final OverlayAddressedMessage<Object> msg) throws Exception {

    }
}
