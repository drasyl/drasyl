package org.drasyl.handler.dht.chord;

import io.netty.channel.ChannelHandlerContext;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.FutureComposer;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.Objects;

import static java.util.Objects.requireNonNull;
import static org.drasyl.handler.dht.chord.ChordUtil.chordIdPosition;
import static org.drasyl.handler.dht.chord.ChordUtil.chordIdToHex;
import static org.drasyl.handler.dht.chord.ChordUtil.ithFingerStart;
import static org.drasyl.handler.dht.chord.requester.ChordIAmPreRequester.iAmPreRequest;
import static org.drasyl.util.FutureComposer.composeFuture;

public class ChordFingerTable {
    private static final Logger LOG = LoggerFactory.getLogger(ChordFingerTable.class);
    private final IdentityPublicKey[] entries;
    private final IdentityPublicKey localAddress;
    private IdentityPublicKey predecessor;

    public ChordFingerTable(final IdentityPublicKey localAddress) {
        this.entries = new IdentityPublicKey[Integer.SIZE];
        this.localAddress = requireNonNull(localAddress);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();

        sb.append("LOCAL:        " + localAddress + " " + chordIdToHex(localAddress) + " (" + chordIdPosition(localAddress) + ")");
        sb.append(System.lineSeparator());
        sb.append("PREDECESSOR:  " + getPredecessor() + " " + (hasPredecessor() ? chordIdToHex(getPredecessor()) + " (" + chordIdPosition(getPredecessor()) + ")" : ""));
        sb.append(System.lineSeparator());
        sb.append("FINGER TABLE:");
        sb.append(System.lineSeparator());

        // header
        sb.append("No.\tStart\t\t\tAddress\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\tId");
        sb.append(System.lineSeparator());

        // body
        for (int i = 0; i < entries.length; i++) {
            final long ithStart = ithFingerStart(localAddress, i + 1);
            sb.append((i + 1) + "\t" + chordIdToHex(ithStart) + " (" + chordIdPosition(ithStart) + ")" + "\t" + entries[i] + "\t" + (entries[i] != null ? chordIdToHex(entries[i]) + " (" + chordIdPosition(entries[i]) + ")" : ""));
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

    public void removePeer(final IdentityPublicKey value) {
        LOG.info("Remove peer `{}` from finger table.", value);
        for (int i = Integer.SIZE; i > 0; i--) {
            final IdentityPublicKey ithfinger = entries[i - 1];
            if (ithfinger != null && ithfinger.equals(value)) {
                entries[i - 1] = null;
            }
        }
    }

    public FutureComposer<Void> setSuccessor(final ChannelHandlerContext ctx,
                                             final IdentityPublicKey successor) {
        return updateIthFinger(ctx, 1, successor);
    }

    public FutureComposer<Void> updateIthFinger(final ChannelHandlerContext ctx,
                                                final int i,
                                                final IdentityPublicKey value) {
        final IdentityPublicKey oldValue = entries[i - 1];
        entries[i - 1] = value;
        if (!Objects.equals(value, oldValue)) {
            LOG.info("Update {}th finger to `{}`.", i, value);
        }

        // if the updated one is successor, notify the new successor
        if (i == 1 && value != null && !value.equals(localAddress)) {
            return iAmPreRequest(ctx, value);
        }
        else {
            return composeFuture();
        }
    }
}
