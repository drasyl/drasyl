package org.drasyl.handler.dht.chord;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.concurrent.FutureListener;
import org.drasyl.channel.OverlayAddressedMessage;
import org.drasyl.handler.dht.chord.message.ChordMessage;
import org.drasyl.handler.dht.chord.message.Closest;
import org.drasyl.handler.dht.chord.message.FindSuccessor;
import org.drasyl.handler.dht.chord.message.FoundSuccessor;
import org.drasyl.handler.dht.chord.message.IAmPre;
import org.drasyl.handler.dht.chord.message.MyClosest;
import org.drasyl.handler.dht.chord.message.MySuccessor;
import org.drasyl.handler.dht.chord.message.NothingSuccessor;
import org.drasyl.handler.dht.chord.message.Notified;
import org.drasyl.handler.dht.chord.message.YourSuccessor;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import static java.util.Objects.requireNonNull;
import static org.drasyl.handler.dht.chord.ChordUtil.chordId;
import static org.drasyl.handler.dht.chord.ChordUtil.relativeChordId;
import static org.drasyl.handler.dht.chord.helper.ChordClosestPrecedingFingerHelper.closestPrecedingFinger;
import static org.drasyl.handler.dht.chord.helper.ChordFindSuccessorHelper.findSuccessor;

/**
 * A handler that listens to {@link ChordMessage}'s sent by other peers.
 * <p>
 * This class is based on <a href="https://github.com/ChuanXia/Chord">Chord implementation of Chuan
 * Xia</a>.
 */
public class ChordListener extends SimpleChannelInboundHandler<OverlayAddressedMessage<ChordMessage>> {
    private static final Logger LOG = LoggerFactory.getLogger(ChordListener.class);
    private final ChordFingerTable fingerTable;

    public ChordListener(final ChordFingerTable fingerTable) {
        this.fingerTable = requireNonNull(fingerTable);
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx,
                                final OverlayAddressedMessage<ChordMessage> msg) throws Exception {
        final DrasylAddress sender = msg.sender();
        LOG.debug("Got `{}` from `{}`.", msg.content(), sender);
        if (msg.content() instanceof Closest) {
            handleClosest(ctx, sender, (Closest) msg.content());
        }
        else if (msg.content() instanceof YourSuccessor) {
            handleYourSuccessor(ctx, sender);
        }
        else if (msg.content() instanceof FindSuccessor) {
            handleFindSuccessor(ctx, sender, (FindSuccessor) msg.content());
        }
        else if (msg.content() instanceof IAmPre) {
            handleIAmPre(ctx, sender);
        }
        else {
            ctx.fireChannelRead(msg);
        }
    }

    /*
     * Message Handlers
     */

    private void handleClosest(final ChannelHandlerContext ctx,
                               final DrasylAddress sender,
                               final Closest closest) {
        final long id = closest.getId();
        closestPrecedingFinger(ctx, id, fingerTable).finish(ctx.executor()).addListener((FutureListener<DrasylAddress>) future -> {
            final DrasylAddress result = future.getNow();
            // FIXME: hier ist result manchmal null -> NPE
            final OverlayAddressedMessage<MyClosest> response = new OverlayAddressedMessage<>(MyClosest.of(result), sender);
            ctx.writeAndFlush(response);
        });
    }

    private void handleYourSuccessor(final ChannelHandlerContext ctx, final DrasylAddress sender) {
        if (fingerTable.hasSuccessor()) {
            final OverlayAddressedMessage<MySuccessor> response = new OverlayAddressedMessage<>(MySuccessor.of(fingerTable.getSuccessor()), sender);
            ctx.writeAndFlush(response);
        }
        else {
            final OverlayAddressedMessage<NothingSuccessor> response = new OverlayAddressedMessage<>(NothingSuccessor.of(), sender);
            ctx.writeAndFlush(response);
        }
    }

    private void handleFindSuccessor(final ChannelHandlerContext ctx,
                                     final DrasylAddress sender,
                                     final FindSuccessor findSuccessor) {
        final long id = findSuccessor.getId();

        findSuccessor(ctx, id, fingerTable).finish(ctx.executor()).addListener((FutureListener<DrasylAddress>) future -> {
            final OverlayAddressedMessage<FoundSuccessor> response = new OverlayAddressedMessage<>(FoundSuccessor.of(future.getNow()), sender);
            ctx.writeAndFlush(response);
        });
    }

    private void handleIAmPre(final ChannelHandlerContext ctx,
                              final DrasylAddress newPredecessorCandidate) {
        LOG.debug("Notified by `{}`.", newPredecessorCandidate);
        if (!fingerTable.hasPredecessor() || ctx.channel().localAddress().equals(fingerTable.getPredecessor())) {
            LOG.info("Set predecessor `{}`.", newPredecessorCandidate);
            fingerTable.setPredecessor(newPredecessorCandidate);
            // FIXME: wieso hier nicht checken, ob er als geeigneter fingers dient?
        }
        else {
            final long oldpre_id = chordId(fingerTable.getPredecessor());
            final long local_relative_id = relativeChordId(ctx.channel().localAddress(), oldpre_id);
            final long newpre_relative_id = relativeChordId(newPredecessorCandidate, oldpre_id);
            if (newpre_relative_id > 0 && newpre_relative_id < local_relative_id) {
                LOG.info("Set predecessor `{}`.", newPredecessorCandidate);
                fingerTable.setPredecessor(newPredecessorCandidate);
            }
        }

        ctx.writeAndFlush(new OverlayAddressedMessage<>(Notified.of(), newPredecessorCandidate));
    }
}
