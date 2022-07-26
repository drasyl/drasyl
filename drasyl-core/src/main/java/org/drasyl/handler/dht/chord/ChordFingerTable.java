package org.drasyl.handler.dht.chord;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.Future;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.FutureUtil;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.Objects;

import static java.util.Objects.requireNonNull;
import static org.drasyl.util.FutureUtil.chainFuture;

public class ChordFingerTable {
    private static final Logger LOG = LoggerFactory.getLogger(ChordFingerTable.class);
    public static final int SIZE = 32;
    private final IdentityPublicKey[] entries;
    private final IdentityPublicKey localAddress;
    private final long localId;
    private IdentityPublicKey predecessor;

    public ChordFingerTable(final IdentityPublicKey localAddress) {
        this.entries = new IdentityPublicKey[SIZE];
        this.localAddress = requireNonNull(localAddress);
        this.localId = ChordUtil.hashSocketAddress(localAddress);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();

        // header
        sb.append("No.\tStart\t\tAddress\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\tId");
        sb.append(System.lineSeparator());

        // body
        for (int i = 0; i < entries.length; i++) {
            //entries[i] = IdentityPublicKey.of("4e563e668f9820c2dbbbe39bc04ea6cfb68ce67777abe15a3258072cdd9ab042");
            final long ithStart = ChordUtil.ithStart(localId, i + 1);
            sb.append((i + 1) + "\t" + ChordUtil.longTo8DigitHex(ithStart) + " (" + ChordUtil.chordPosition(ithStart) + ")" + "\t" + entries[i] + "\t" + (entries[i] != null ? ChordUtil.longTo8DigitHex(ChordUtil.hashSocketAddress(entries[i])) + " (" + ChordUtil.chordPosition(ChordUtil.hashSocketAddress(entries[i])) + ")" : ""));
            sb.append(System.lineSeparator());
        }

        return sb.toString();
    }

    public IdentityPublicKey get(final int i) {
        return entries[i - 1];
    }

    public IdentityPublicKey getSuccessor() {
        return get(1);
    }

    public boolean hasSuccessor() {
        return getSuccessor() != null;
    }

    public Future<Void> setSuccessor(final ChannelHandlerContext ctx,
                                     final IdentityPublicKey successor) {
        return updateIthFinger(ctx, 1, successor);
    }

    public IdentityPublicKey getPredecessor() {
        return predecessor;
    }

    public boolean hasPredecessor() {
        return getPredecessor() != null;
    }

    public void setPredecessor(final IdentityPublicKey predecessor) {
        this.predecessor = predecessor;
    }

    public void removePredecessor() {
        setPredecessor(null);
    }

    // i = 1..32
    public Future<Void> updateIthFinger(final ChannelHandlerContext ctx,
                                        final int i,
                                        final IdentityPublicKey value) {
        final IdentityPublicKey oldValue = entries[i - 1];
        entries[i - 1] = value;
        if (!Objects.equals(value, oldValue)) {
            LOG.info("Update {}th finger to `{}`.", i, value);
        }

        // if the updated one is successor, notify the new successor
        if (i == 1 && value != null && !value.equals(localAddress)) {
            return notify(ctx, value);
        }
        else {
            return ctx.executor().newSucceededFuture(null);
        }
    }

    public void removePeer(final IdentityPublicKey value) {
        LOG.info("Remove peer `{}` from finger table.", value);
        for (int i = 32; i > 0; i--) {
            final IdentityPublicKey ithfinger = entries[i - 1];
            if (ithfinger != null && ithfinger.equals(value)) {
                entries[i - 1] = null;
            }
        }
    }

    public Future<Void> notify(final ChannelHandlerContext ctx, final IdentityPublicKey successor) {
        if (successor != null && !successor.equals(localAddress)) {
            return ChordUtil.requestIAmPre(ctx, successor);
        }
        return ctx.executor().newSucceededFuture(null);
    }

    public Future<Void> deleteSuccessor(final ChannelHandlerContext ctx) {
        final IdentityPublicKey successor = getSuccessor();

        //nothing to delete, just return
        if (successor == null) {
            return null;
        }

        // find the last existence of successor in the finger table
        int i;
        for (i = 32; i > 0; i--) {
            final IdentityPublicKey ithfinger = entries[i - 1];
            if (ithfinger != null && ithfinger.equals(successor)) {
                break;
            }
        }

        // delete it, from the last existence to the first one
        deleteSuccessor_recursive(ctx, i);

        // if predecessor is successor, delete it
        if (hasPredecessor() && Objects.equals(getPredecessor(), getSuccessor())) {
            removePredecessor();
        }

        // try to fill successor
        return FutureUtil.chainFuture(ChordUtil.fillSuccessor(ctx, this), ctx.executor(), unused -> {
            final IdentityPublicKey successor2 = getSuccessor();

            // if successor is still null or local node,
            // and the predecessor is another node, keep asking
            // it's predecessor until find local node's new successor
            if ((successor2 == null || localAddress.equals(successor2)) && hasPredecessor() && !localAddress.equals(getPredecessor())) {
                final IdentityPublicKey p = getPredecessor();

                final Future<Void> voidFuture = FutureUtil.mapFuture(deleteSuccessor_recursive2(ctx, p, successor2), ctx.executor(), publicKey -> null);
                return chainFuture(voidFuture, ctx.executor(), publicKey -> {
                    // update successor
                    return updateIthFinger(ctx, 1, p);
                });
            }
            else {
                return ctx.executor().newSucceededFuture(null);
            }
        });
    }

    private Future<IdentityPublicKey> deleteSuccessor_recursive2(final ChannelHandlerContext ctx,
                                                                 final IdentityPublicKey p,
                                                                 final IdentityPublicKey successor) {
        return chainFuture(ChordUtil.requestYourPredecessor(ctx, p), ctx.executor(), p_pre -> {
            if (p_pre == null) {
                return ctx.executor().newSucceededFuture(p);
            }

            // if p's predecessor is node is just deleted,
            // or itself (nothing found in p), or local address,
            // p is current node's new successor, break
            if (p_pre.equals(p) || p_pre.equals(localAddress) || p_pre.equals(successor)) {
                return ctx.executor().newSucceededFuture(p);
            }

            // else, keep asking
            else {
                return deleteSuccessor_recursive2(ctx, p_pre, successor);
            }
        });
    }

    private Future<Void> deleteSuccessor_recursive(final ChannelHandlerContext ctx, final int j) {
        return chainFuture(updateIthFinger(ctx, j, null), ctx.executor(), unused -> {
            if (j > 1) {
                return deleteSuccessor_recursive(ctx, j - 1);
            }
            else {
                return updateIthFinger(ctx, j, null);
            }
        });
    }
}
