package org.drasyl.handler.dht.chord;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.concurrent.FutureListener;
import org.drasyl.channel.OverlayAddressedMessage;
import org.drasyl.handler.dht.chord.message.Alive;
import org.drasyl.handler.dht.chord.message.ChordMessage;
import org.drasyl.handler.dht.chord.message.Closest;
import org.drasyl.handler.dht.chord.message.FindSuccessor;
import org.drasyl.handler.dht.chord.message.FoundSuccessor;
import org.drasyl.handler.dht.chord.message.IAmPre;
import org.drasyl.handler.dht.chord.message.Keep;
import org.drasyl.handler.dht.chord.message.MyClosest;
import org.drasyl.handler.dht.chord.message.MyPredecessor;
import org.drasyl.handler.dht.chord.message.MySuccessor;
import org.drasyl.handler.dht.chord.message.NothingPredecessor;
import org.drasyl.handler.dht.chord.message.Notified;
import org.drasyl.handler.dht.chord.message.YourPredecessor;
import org.drasyl.handler.dht.chord.message.YourSuccessor;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import static java.util.Objects.requireNonNull;

public class ChordTalker extends SimpleChannelInboundHandler<OverlayAddressedMessage<ChordMessage>> {
    private static final Logger LOG = LoggerFactory.getLogger(ChordTalker.class);
    private final ChordFingerTable fingerTable;

    public ChordTalker(final ChordFingerTable fingerTable) {
        this.fingerTable = requireNonNull(fingerTable);
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx,
                                final OverlayAddressedMessage<ChordMessage> msg) throws Exception {
        final DrasylAddress sender = msg.sender();
        if (msg.content() instanceof Closest) {
            handleClosest(ctx, sender, (Closest) msg.content());
        }
        else if (msg.content() instanceof YourSuccessor) {
            handleYourSuccessor(ctx, sender);
        }
        else if (msg.content() instanceof YourPredecessor) {
            handleYourPredecessor(ctx, sender);
        }
        else if (msg.content() instanceof FindSuccessor) {
            handleFindSuccessor(ctx, sender, (FindSuccessor) msg.content());
        }
        else if (msg.content() instanceof IAmPre) {
            handleIAmPre(ctx, (IdentityPublicKey) sender);
        }
        else if (msg.content() instanceof Keep) {
            handleKeep(ctx, sender);
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
        ChordUtil.closest_preceding_finger(ctx, id, fingerTable).addListener((FutureListener<IdentityPublicKey>) future -> {
            final IdentityPublicKey result = future.get();
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
            final OverlayAddressedMessage<NothingPredecessor> response = new OverlayAddressedMessage<>(NothingPredecessor.of(), sender);
            ctx.writeAndFlush(response);
        }
    }

    private void handleYourPredecessor(final ChannelHandlerContext ctx,
                                       final DrasylAddress sender) {
        if (fingerTable.hasPredecessor()) {
            final OverlayAddressedMessage<MyPredecessor> response = new OverlayAddressedMessage<>(MyPredecessor.of(fingerTable.getPredecessor()), sender);
            ctx.writeAndFlush(response);
        }
        else {
            final OverlayAddressedMessage<NothingPredecessor> response = new OverlayAddressedMessage<>(NothingPredecessor.of(), sender);
            ctx.writeAndFlush(response);
        }
    }

    private void handleFindSuccessor(final ChannelHandlerContext ctx,
                                     final DrasylAddress sender,
                                     final FindSuccessor findSuccessor) {
        final long id = findSuccessor.getId();

        ChordUtil.find_successor(ctx, id, fingerTable).addListener((FutureListener<IdentityPublicKey>) future -> {
            final OverlayAddressedMessage<FoundSuccessor> response = new OverlayAddressedMessage<>(FoundSuccessor.of(future.getNow()), sender);
            ctx.writeAndFlush(response);
        });
    }

    private void handleIAmPre(final ChannelHandlerContext ctx,
                              final IdentityPublicKey newPredecessorCandiate) {
        LOG.debug("Notified by `{}`.", newPredecessorCandiate);
        if (!fingerTable.hasPredecessor() || ctx.channel().localAddress().equals(fingerTable.getPredecessor())) {
            LOG.info("Set predecessor `{}`.", newPredecessorCandiate);
            fingerTable.setPredecessor(newPredecessorCandiate);
        }
        else {
            final long oldpre_id = ChordUtil.hashSocketAddress(fingerTable.getPredecessor());
            final long local_relative_id = ChordUtil.computeRelativeId(ChordUtil.hashSocketAddress((IdentityPublicKey) ctx.channel().localAddress()), oldpre_id);
            final long newpre_relative_id = ChordUtil.computeRelativeId(ChordUtil.hashSocketAddress(newPredecessorCandiate), oldpre_id);
            if (newpre_relative_id > 0 && newpre_relative_id < local_relative_id) {
                LOG.info("Set predecessor `{}`.", newPredecessorCandiate);
                fingerTable.setPredecessor(newPredecessorCandiate);
            }
        }

        ctx.writeAndFlush(new OverlayAddressedMessage<>(Notified.of(), newPredecessorCandiate));
    }

    private void handleKeep(final ChannelHandlerContext ctx, final DrasylAddress sender) {
        final OverlayAddressedMessage<Alive> response = new OverlayAddressedMessage<>(Alive.of(), sender);
        ctx.writeAndFlush(response);
    }
}
