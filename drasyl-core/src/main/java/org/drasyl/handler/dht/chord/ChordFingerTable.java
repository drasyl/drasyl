package org.drasyl.handler.dht.chord;

import io.netty.channel.ChannelHandlerContext;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.FutureComposer;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.Objects;

import static java.util.Objects.requireNonNull;
import static org.drasyl.handler.dht.chord.ChordUtil.chordIdHex;
import static org.drasyl.handler.dht.chord.ChordUtil.chordIdPosition;
import static org.drasyl.handler.dht.chord.ChordUtil.ithFingerStart;
import static org.drasyl.handler.dht.chord.requester.ChordIAmPreRequester.iAmPreRequest;
import static org.drasyl.util.FutureComposer.composeFuture;

public class ChordFingerTable {
    private static final Logger LOG = LoggerFactory.getLogger(ChordFingerTable.class);
    private final DrasylAddress[] entries;
    private final DrasylAddress localAddress;
    private DrasylAddress predecessor;

    public ChordFingerTable(final DrasylAddress localAddress) {
        this.entries = new DrasylAddress[Integer.SIZE];
        this.localAddress = requireNonNull(localAddress);
    }

    @SuppressWarnings("StringConcatenationInsideStringBufferAppend")
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();

        sb.append("LOCAL:        " + localAddress + " " + chordIdHex(localAddress) + " (" + chordIdPosition(localAddress) + ")");
        sb.append(System.lineSeparator());
        sb.append("PREDECESSOR:  " + getPredecessor() + " " + (hasPredecessor() ? chordIdHex(getPredecessor()) + " (" + chordIdPosition(getPredecessor()) + ")" : ""));
        sb.append(System.lineSeparator());
        sb.append("FINGER TABLE:");
        sb.append(System.lineSeparator());

        // header
        sb.append("No.\tStart\t\t\tAddress\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\tId");
        sb.append(System.lineSeparator());

        // body
        for (int i = 1; i <= entries.length; i++) {
            final long ithStart = ithFingerStart(localAddress, i);
            sb.append(i + "\t" + ChordUtil.chordIdHex(ithStart) + " (" + chordIdPosition(ithStart) + ")" + "\t" + entries[i - 1] + "\t" + (entries[i - 1] != null ? chordIdHex(entries[i - 1]) + " (" + chordIdPosition(entries[i - 1]) + ")" : ""));
            sb.append(System.lineSeparator());
        }

        return sb.toString();
    }

    public DrasylAddress get(final int i) {
        return entries[i - 1];
    }

    public DrasylAddress getSuccessor() {
        return get(1);
    }

    public boolean hasSuccessor() {
        return getSuccessor() != null;
    }

    public DrasylAddress getPredecessor() {
        return predecessor;
    }

    public boolean hasPredecessor() {
        return getPredecessor() != null;
    }

    public void setPredecessor(final DrasylAddress predecessor) {
        this.predecessor = predecessor;
    }

    public void removePredecessor() {
        setPredecessor(null);
    }

    public void removePeer(final DrasylAddress value) {
        LOG.info("Remove peer `{}` from finger table.", value);
        for (int i = Integer.SIZE; i > 0; i--) {
            final DrasylAddress ithFinger = entries[i - 1];
            if (ithFinger != null && ithFinger.equals(value)) {
                entries[i - 1] = null;
            }
        }
    }

    public FutureComposer<Void> setSuccessor(final ChannelHandlerContext ctx,
                                             final DrasylAddress successor) {
        return updateIthFinger(ctx, 1, successor);
    }

    public FutureComposer<Void> updateIthFinger(final ChannelHandlerContext ctx,
                                                final int i,
                                                final DrasylAddress value) {
        final DrasylAddress oldValue = entries[i - 1];
        entries[i - 1] = value;
        if (!Objects.equals(value, oldValue)) {
            LOG.info("Update {}th finger to `{}` ({} -> {} -> {}).", i, value, oldValue != null ? chordIdPosition(oldValue) : "null", value != null ? chordIdPosition(value) : "null", chordIdPosition(ithFingerStart(localAddress, i)));
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
