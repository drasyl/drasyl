package org.drasyl.handler.dht.chord.message;

import com.google.auto.value.AutoValue;
import org.drasyl.identity.IdentityPublicKey;

@AutoValue
public abstract class FoundSuccessor implements ChordMessage {
    public abstract IdentityPublicKey getAddress();

    public static FoundSuccessor of(final IdentityPublicKey address) {
        return new AutoValue_FoundSuccessor(address);
    }
}
