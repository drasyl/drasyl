package org.drasyl.handler.dht.chord;

import org.drasyl.identity.IdentityPublicKey;

public class ChordFingerTable {
    public static final int SIZE = 32;
    private final IdentityPublicKey[] entries;
    private final long localId;

    public ChordFingerTable(final long localId) {
        this.entries = new IdentityPublicKey[SIZE];
        this.localId = localId;
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
            sb.append((i + 1) + "\t" + ChordUtil.chordIdToTex(ithStart) + "\t" + entries[i] + "\t" + (entries[i] != null ? ChordUtil.chordIdToTex(ChordUtil.chordId(entries[i])) : ""));
            sb.append(System.lineSeparator());
        }

        return sb.toString();
    }

    public static long ithStart(final long id, final int i) {
        return (long) ((id + Math.pow(2, i)) % Math.pow(2, 32));
    }
}
