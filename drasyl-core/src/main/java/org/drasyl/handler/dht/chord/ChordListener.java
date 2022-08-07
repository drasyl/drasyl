package org.drasyl.handler.dht.chord;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.concurrent.FutureListener;
import org.drasyl.channel.OverlayAddressedMessage;
import org.drasyl.handler.dht.chord.message.ChordMessage;
import org.drasyl.handler.dht.chord.message.Closest;
import org.drasyl.handler.dht.chord.message.FindSuccessor;
import org.drasyl.handler.dht.chord.message.FoundSuccessor;
import org.drasyl.handler.dht.chord.message.MyClosest;
import org.drasyl.handler.rmi.RmiClientHandler;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import static java.util.Objects.requireNonNull;
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
        else if (msg.content() instanceof FindSuccessor) {
            handleFindSuccessor(ctx, sender, (FindSuccessor) msg.content());
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
        closestPrecedingFinger(id, fingerTable, ctx.pipeline().get(RmiClientHandler.class)).finish(ctx.executor()).addListener((FutureListener<DrasylAddress>) future -> {
            final DrasylAddress result = future.getNow();
            // FIXME: hier ist result manchmal null -> NPE
            final OverlayAddressedMessage<MyClosest> response = new OverlayAddressedMessage<>(MyClosest.of(result), sender);
            ctx.writeAndFlush(response);
        });
    }

    private void handleFindSuccessor(final ChannelHandlerContext ctx,
                                     final DrasylAddress sender,
                                     final FindSuccessor findSuccessor) {
        final long id = findSuccessor.getId();

        findSuccessor(ctx, id, fingerTable, ctx.pipeline().get(RmiClientHandler.class)).finish(ctx.executor()).addListener((FutureListener<DrasylAddress>) future -> {
            final OverlayAddressedMessage<FoundSuccessor> response = new OverlayAddressedMessage<>(FoundSuccessor.of(future.getNow()), sender);
            ctx.writeAndFlush(response);
        });
    }
}
