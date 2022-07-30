package org.drasyl.handler.dht.chord;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.concurrent.Future;
import org.drasyl.channel.OverlayAddressedMessage;
import org.drasyl.handler.dht.chord.message.ChordMessage;
import org.drasyl.handler.dht.chord.message.FindSuccessor;
import org.drasyl.handler.dht.chord.message.FoundSuccessor;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.handler.dht.chord.ChordUtil.chordId;
import static org.drasyl.handler.dht.chord.ChordUtil.chordIdHex;
import static org.drasyl.util.Preconditions.requirePositive;

/**
 * Joins the Chord distributed hash table.
 */
public class ChordJoinHandler extends SimpleChannelInboundHandler<OverlayAddressedMessage<FoundSuccessor>> {
    private static final Logger LOG = LoggerFactory.getLogger(ChordJoinHandler.class);
    private final ChordFingerTable fingerTable;
    private final DrasylAddress contact;
    private final int joinTimeoutMillis;
    private Future<?> joinTaskFuture;

    public ChordJoinHandler(final ChordFingerTable fingerTable,
                            final DrasylAddress contact,
                            final int joinTimeoutMillis) {
        this.fingerTable = requireNonNull(fingerTable);
        this.contact = requireNonNull(contact);
        this.joinTimeoutMillis = requirePositive(joinTimeoutMillis);
    }

    public ChordJoinHandler(final ChordFingerTable fingerTable, final DrasylAddress contact) {
        this(fingerTable, contact, 5_000);
    }

    /*
     * Handler Events
     */

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        if (ctx.channel().isActive()) {
            doJoinTask(ctx);
        }
    }

    @Override
    public void handlerRemoved(final ChannelHandlerContext ctx) {
        cancelJoinTask();
    }

    /*
     * Channel Events
     */

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        doJoinTask(ctx);
        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        cancelJoinTask();
        ctx.fireChannelInactive();
    }

    @Override
    public boolean acceptInboundMessage(final Object msg) {
        return msg instanceof OverlayAddressedMessage && ((OverlayAddressedMessage<?>) msg).content() instanceof FoundSuccessor;
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx,
                                final OverlayAddressedMessage<FoundSuccessor> msg) {
        final DrasylAddress successor = msg.content().getAddress();
        LOG.info("Successor for id `{}` is `{}`.", chordIdHex(ctx.channel().localAddress()), successor);
        LOG.info("Set `{}` as our successor.", successor);
        fingerTable.setSuccessor(ctx, successor).finish(ctx.executor());
        ctx.pipeline().remove(ctx.name());
    }

    /*
     * Join Task
     */

    private void doJoinTask(final ChannelHandlerContext ctx) {
        LOG.info("Join DHT ring by asking `{}` to find the successor for my id `{}`.", contact, chordIdHex(ctx.channel().localAddress()));
        final ChordMessage msg = FindSuccessor.of(chordId(ctx.channel().localAddress()));
        ctx.writeAndFlush(new OverlayAddressedMessage<>(msg, contact)).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                // create timeout guard
                joinTaskFuture = ctx.executor().schedule(() -> {
                    LOG.error("Got no response from `{}` within 5000ms.", contact);
                    failJoinTask(ctx, new ChordException("Cannot find node you are trying to contact. Please exit."));
                }, joinTimeoutMillis, MILLISECONDS);
            }
            else {
                failJoinTask(ctx, new Exception("Unable to join DHT ring.", future.cause()));
            }
        });
    }

    private void failJoinTask(final ChannelHandlerContext ctx, final Throwable cause) {
        ctx.pipeline().fireExceptionCaught(cause);
        ctx.pipeline().close();
    }

    private void cancelJoinTask() {
        if (joinTaskFuture != null) {
            joinTaskFuture.cancel(false);
            joinTaskFuture = null;
        }
    }
}
