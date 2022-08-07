package org.drasyl.handler.dht.chord;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.concurrent.FutureListener;
import org.drasyl.handler.rmi.RmiClientHandler;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import static java.util.Objects.requireNonNull;
import static org.drasyl.handler.dht.chord.ChordUtil.chordId;
import static org.drasyl.handler.dht.chord.ChordUtil.chordIdHex;

/**
 * Joins the Chord distributed hash table.
 */
public class ChordJoinHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(ChordJoinHandler.class);
    private final ChordFingerTable fingerTable;
    private final DrasylAddress contact;

    public ChordJoinHandler(final ChordFingerTable fingerTable,
                            final DrasylAddress contact) {
        this.fingerTable = requireNonNull(fingerTable);
        this.contact = requireNonNull(contact);
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

    /*
     * Channel Events
     */

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        doJoinTask(ctx);
        ctx.fireChannelActive();
    }

    /*
     * Join Task
     */

    private void doJoinTask(final ChannelHandlerContext ctx) {
        LOG.info("Join DHT ring by asking `{}` to find the successor for my id `{}`.", contact, chordIdHex(ctx.channel().localAddress()));
        final RmiClientHandler client = ctx.pipeline().get(RmiClientHandler.class);
        final ChordService service = client.lookup("ChordService", ChordService.class, contact);
        service.findSuccessor(chordId(ctx.channel().localAddress())).addListener((FutureListener<DrasylAddress>) future -> {
            if (future.isSuccess()) {
                final DrasylAddress successor = future.getNow();
                LOG.info("Successor for id `{}` is `{}`.", chordIdHex(ctx.channel().localAddress()), successor);
                LOG.info("Set `{}` as our successor.", successor);
                fingerTable.setSuccessor(successor, client).finish(ctx.executor());
            }
            else {
                LOG.error("Got no response from `{}` within 5000ms.", contact); // FIXME: 60000ms eigentlich. wäre cool, wenn man das ändern könnte!
                ctx.pipeline().fireExceptionCaught(new ChordException("Cannot find node you are trying to contact. Please exit."));
                ctx.pipeline().close();
            }
        });
    }
}
