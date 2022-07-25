package org.drasyl.handler.dht.chord.message;

import com.google.auto.value.AutoValue;
import org.drasyl.identity.IdentityPublicKey;

@AutoValue
public abstract class YourSuccessor implements ChordMessage {
    public abstract IdentityPublicKey getAddress();

    public static YourSuccessor of(final IdentityPublicKey address) {
        return new AutoValue_YourSuccessor(address);
    }
}
