package org.drasyl.handler.dht.chord;

import io.netty.channel.ChannelHandlerContext;
import org.drasyl.channel.OverlayAddressedMessage;
import org.drasyl.handler.dht.chord.message.IAmPre;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.Objects;

public class ChordFingerTable {
    private static final Logger LOG = LoggerFactory.getLogger(ChordFingerTable.class);
    public static final int SIZE = 32;
    private final IdentityPublicKey[] entries;
    private final IdentityPublicKey localAddress;
    private final long localId;

    public ChordFingerTable(final IdentityPublicKey localAddress) {
        this.entries = new IdentityPublicKey[SIZE];
        this.localAddress = Objects.requireNonNull(localAddress);
        this.localId = ChordUtil.chordId(localAddress);
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
            final long ithStart = ithStart(localId, i);
            sb.append((i + 1) + "\t" + ChordUtil.chordIdToHex(ithStart) + "\t" + entries[i] + "\t" + (entries[i] != null ? ChordUtil.chordIdToHex(ChordUtil.chordId(entries[i])) : ""));
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

    public void setSuccessor(final ChannelHandlerContext ctx, final IdentityPublicKey successor) {
        updateIthFinger(ctx, 1, successor);
    }

    private void updateIthFinger(final ChannelHandlerContext ctx,
                                 final int i,
                                 final IdentityPublicKey value) {
        final IdentityPublicKey oldValue = entries[i - 1];
        entries[i - 1] = value;
        if (!Objects.equals(value, oldValue)) {
            LOG.info("Update {}th finger to `{}`.", i, value);
        }

        // if the updated one is successor, notify the new successor
        if (i == 1 && value != null && !value.equals(localAddress)) {
            notify(ctx, value);
        }
    }

    private void notify(final ChannelHandlerContext ctx, final IdentityPublicKey successor) {
        if (successor != null && !successor.equals(localAddress)) {
            ctx.writeAndFlush(new OverlayAddressedMessage<>(IAmPre.of(localAddress), successor));
        }
    }

    public static long ithStart(final long id, final int i) {
        return (long) ((id + Math.pow(2, i)) % Math.pow(2, 32));
    }
}
