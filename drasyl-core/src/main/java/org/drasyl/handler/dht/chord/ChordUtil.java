package org.drasyl.handler.dht.chord;

import org.drasyl.crypto.HexUtil;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.IdentityPublicKey;

import java.nio.ByteBuffer;

public final class ChordUtil {
    public static final int[] POWER_OF_TWO = new int[32];

    static {
        int value = 1;
        for (int i = 0; i < POWER_OF_TWO.length; i++) {
            POWER_OF_TWO[i] = value;
            value *= 2;
        }
    }

    private ChordUtil() {
        // util class
    }

    public static long chordId(final IdentityPublicKey address) {
        return ByteBuffer.allocate(Long.BYTES).put(address.toByteArray(), 0, Integer.BYTES).putInt(0).position(0).getInt();
    }

    public static long chordId(final DrasylAddress address) {
        return chordId((IdentityPublicKey) address);
    }

    public static String chordIdToTex(final long id) {
        final ByteBuffer buf = ByteBuffer.allocate(Long.BYTES).putLong(id).position(Integer.BYTES);
        final byte[] a = new byte[buf.position()];
        buf.get(a);
        return HexUtil.bytesToHex(a);
    }
}
