package org.drasyl.handler.dht.chord;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.concurrent.Future;
import org.drasyl.channel.OverlayAddressedMessage;
import org.drasyl.handler.dht.chord.message.ChordMessage;
import org.drasyl.handler.dht.chord.message.FindSuccessor;
import org.drasyl.handler.dht.chord.message.FoundSuccessor;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class ChordJoinHandler extends SimpleChannelInboundHandler<OverlayAddressedMessage<FoundSuccessor>> {
    private static final Logger LOG = LoggerFactory.getLogger(ChordJoinHandler.class);
    private final ChordFingerTable fingerTable;
    private final IdentityPublicKey contact;
    private Future<?> joinTimeoutGuard;

    public ChordJoinHandler(final ChordFingerTable fingerTable, final IdentityPublicKey contact) {
        this.fingerTable = requireNonNull(fingerTable);
        this.contact = requireNonNull(contact);
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        if (ctx.channel().isActive()) {
            doJoin(ctx);
        }
    }

    @Override
    public void handlerRemoved(final ChannelHandlerContext ctx) {
        cancelJoinTimeoutGuard();
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        cancelJoinTimeoutGuard();
        ctx.fireChannelInactive();
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        doJoin(ctx);
        ctx.fireChannelActive();
    }

    private void doJoin(final ChannelHandlerContext ctx) {
        LOG.info("Join DHT ring by asking `{}` to find the successor for my id `{}`.", contact, ChordUtil.longTo8DigitHex(ChordUtil.hashSocketAddress((IdentityPublicKey) ctx.channel().localAddress())));
        final ChordMessage msg = FindSuccessor.of(ChordUtil.hashSocketAddress((IdentityPublicKey) ctx.channel().localAddress()));
        ctx.writeAndFlush(new OverlayAddressedMessage<>(msg, contact)).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                // create timeout guard
                joinTimeoutGuard = ctx.executor().schedule(() -> {
                    LOG.error("Got no response from `{}` within 5000ms.", contact);
                    ctx.pipeline().fireExceptionCaught(new ChordException("Cannot find node you are trying to contact. Please exit."));
                    ctx.pipeline().close();
                    System.exit(1);
                }, 5_000, MILLISECONDS);
            }
            else {
                ctx.pipeline().fireExceptionCaught(new Exception("Unable to join DHT ring.", future.cause()));
                ctx.channel().close();
            }
        });
    }

    private void cancelJoinTimeoutGuard() {
        if (joinTimeoutGuard != null) {
            joinTimeoutGuard.cancel(false);
        }
    }

    @Override
    public boolean acceptInboundMessage(final Object msg) throws Exception {
        return msg instanceof OverlayAddressedMessage && ((OverlayAddressedMessage<?>) msg).content() instanceof FoundSuccessor;
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx,
                                final OverlayAddressedMessage<FoundSuccessor> msg) throws Exception {
        cancelJoinTimeoutGuard();

        final IdentityPublicKey successor = msg.content().getAddress();
        LOG.info("Successor for id `{}` is `{}`.", ChordUtil.longTo8DigitHex(ChordUtil.hashSocketAddress((IdentityPublicKey) ctx.channel().localAddress())), successor);
        LOG.info("Set `{}` as our successor.", successor);
        fingerTable.setSuccessor(ctx, successor);
    }
}
