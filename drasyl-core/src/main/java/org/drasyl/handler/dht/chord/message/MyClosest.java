package org.drasyl.handler.dht.chord.message;

import com.google.auto.value.AutoValue;
import org.drasyl.identity.IdentityPublicKey;

@AutoValue
public abstract class MyClosest implements ChordMessage {
    public abstract IdentityPublicKey getAddress();

    public static MyClosest of(final IdentityPublicKey address) {
        return new AutoValue_MyClosest(address);
    }
}
