package org.drasyl.handler.dht.chord.message;

import com.google.auto.value.AutoValue;
import org.drasyl.identity.IdentityPublicKey;

@AutoValue
public abstract class MySuccessor implements ChordMessage {
    public abstract IdentityPublicKey getAddress();

    public static MySuccessor of(final IdentityPublicKey address) {
        return new AutoValue_MySuccessor(address);
    }
}
