package org.drasyl.handler.dht.chord;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.concurrent.FutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class ChordAskPredecessor extends ChannelInboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(ChordAskPredecessor.class);
    private final ChordFingerTable fingerTable;

    public ChordAskPredecessor(final ChordFingerTable fingerTable) {
        this.fingerTable = requireNonNull(fingerTable);
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        schedulePredecessorAliveCheck(ctx);
        ctx.fireChannelActive();
    }

    private void schedulePredecessorAliveCheck(final ChannelHandlerContext ctx) {
        ctx.executor().schedule(() -> {
            if (fingerTable.hasPredecessor()) {
                LOG.debug("Check if our predecessor is still alive.");
                ChordUtil.requestKeep(ctx, fingerTable.getPredecessor()).addListener((FutureListener<Void>) future -> {
                    if (future.cause() != null) {
                        // timeout
                        LOG.info("Our predecessor is not longer alive. Clear predecessor.");
                        fingerTable.removePredecessor();
                    }
                    schedulePredecessorAliveCheck(ctx);
                });
            }
            else {
                schedulePredecessorAliveCheck(ctx);
            }
        }, 500, MILLISECONDS);
    }
}
