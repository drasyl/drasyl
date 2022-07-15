package org.drasyl.handler.membership.cyclon;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.drasyl.channel.OverlayAddressedMessage;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

/**
 * Waits for CYCLON shuffle requests.
 */
public class CyclonShufflingServerHandler extends SimpleChannelInboundHandler<OverlayAddressedMessage<CyclonShuffleRequest>> {
    private static final Logger LOG = LoggerFactory.getLogger(CyclonShufflingServerHandler.class);

    @Override
    public boolean acceptInboundMessage(final Object message) {
        return message instanceof OverlayAddressedMessage && ((OverlayAddressedMessage<?>) message).content() instanceof CyclonShuffleRequest;
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx,
                                final OverlayAddressedMessage<CyclonShuffleRequest> msg) throws Exception {
        System.out.println();
    }
}
