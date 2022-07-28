package org.drasyl.handler.dht.chord;

import org.drasyl.crypto.HexUtil;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.Sha;

import java.net.SocketAddress;
import java.nio.ByteBuffer;

public final class ChordUtil {
    public static final long[] POWER_OF_TWO = new long[1 + Integer.SIZE];

    static {
        long value = 1;
        for (int i = 0; i < POWER_OF_TWO.length; i++) {
            POWER_OF_TWO[i] = value;
            value *= 2;
        }
    }

    private ChordUtil() {
        // util class
    }

    /*
     *
     */

    /**
     * Returns the start id for the {@code i}th finger.
     *
     * @param i between 1 and 32
     */
    public static long ithFingerStart(final long localId, final int i) {
        return (localId + POWER_OF_TWO[i - 1]) % POWER_OF_TWO[Integer.SIZE];
    }

    public static long ithFingerStart(final SocketAddress localId, final int i) {
        return ithFingerStart(chordId(localId), i);
    }

    public static long chordId(final byte[] bytes) {
        return ByteBuffer.allocate(Long.BYTES).putInt(0).put(Sha.sha1(bytes), 0, Integer.BYTES).position(0).getLong();
    }

    public static long chordId(final DrasylAddress address) {
        return chordId(address.toByteArray());
    }

    public static long chordId(final SocketAddress address) {
        return chordId((DrasylAddress) address);
    }

    public static long chordId(final String s) {
        return chordId(s.getBytes());
    }

    public static String chordIdToHex(final long id) {
        final ByteBuffer buf = ByteBuffer.allocate(Long.BYTES).putLong(id).position(Integer.BYTES);
        final byte[] a = new byte[buf.position()];
        buf.get(a);
        return HexUtil.bytesToHex(a);
    }

    public static String chordIdToHex(final SocketAddress id) {
        return chordIdToHex(chordId(id));
    }

    public static String chordIdPosition(final long id) {
        return id * 100 / POWER_OF_TWO[Integer.SIZE] + "%";
    }

    public static String chordIdPosition(final DrasylAddress id) {
        return chordIdPosition(chordId(id));
    }

    public static long computeRelativeChordId(final long universal, final long local) {
        long ret = universal - local;
        if (ret < 0) {
            ret += POWER_OF_TWO[Integer.SIZE];
        }
        return ret;
    }

    public static long computeRelativeChordId(final SocketAddress universal, final long local) {
        return computeRelativeChordId(chordId(universal), local);
    }
}
