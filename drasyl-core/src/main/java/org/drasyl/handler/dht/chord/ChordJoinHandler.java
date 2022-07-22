package org.drasyl.handler.dht.chord;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.drasyl.channel.OverlayAddressedMessage;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class ChordJoinHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(ChordJoinHandler.class);
    private final long myId;
    private final IdentityPublicKey contact;

    public ChordJoinHandler(final long myId, final IdentityPublicKey contact) {
        this.myId = myId;
        this.contact = requireNonNull(contact);
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        if (ctx.channel().isActive()) {
            doJoin(ctx);
        }
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) throws Exception {
        doJoin(ctx);
        ctx.fireChannelActive();
    }

    private void doJoin(final ChannelHandlerContext ctx) {
        LOG.info("Join DHT ring by asking `{}` to find the successor for my id `{}`.", contact, ChordUtil.chordIdToTex(myId));
        final ChordMessage msg = FindSuccessor.of(myId);
        ctx.writeAndFlush(new OverlayAddressedMessage<>(msg, contact)).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                // wait for response
                ctx.executor().schedule(() -> {
                    System.out.println("TIMEOUT!");
                }, 5_000, MILLISECONDS);
            }
            else {
                ctx.pipeline().fireExceptionCaught(new Exception("Unable to join DHT ring.", future.cause()));
                ctx.channel().close();
            }
        });
    }
}
