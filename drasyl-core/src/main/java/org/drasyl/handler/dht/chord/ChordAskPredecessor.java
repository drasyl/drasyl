package org.drasyl.handler.dht.chord;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.concurrent.FutureListener;
import org.drasyl.identity.IdentityPublicKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class ChordAskPredecessor extends ChannelInboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(ChordAskPredecessor.class);
    private final AtomicReference<IdentityPublicKey> predecessor;

    public ChordAskPredecessor(final AtomicReference<IdentityPublicKey> predecessor) {
        this.predecessor = requireNonNull(predecessor);
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        extracted(ctx);
        ctx.fireChannelActive();
    }

    private void extracted(final ChannelHandlerContext ctx) {
        ctx.executor().schedule(() -> {
            final IdentityPublicKey publicKey = predecessor.get();
            if (publicKey != null) {
                LOG.debug("Check if our predecessor is still alive.");
                ChordUtil.requestKeep(ctx, publicKey).addListener((FutureListener<Void>) future -> {
                    if (future.cause() != null) {
                        LOG.info("Our predecessor is not longer alive. Clear predecessor.");
                        predecessor.set(null);
                    }
                });
            }
            else {
                extracted(ctx);
            }
        }, 500, MILLISECONDS);
    }
}
