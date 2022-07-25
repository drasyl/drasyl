package org.drasyl.handler.dht.chord.message;

import com.google.auto.value.AutoValue;
import org.drasyl.identity.IdentityPublicKey;

@AutoValue
public abstract class IAmPre implements ChordMessage {
    public abstract IdentityPublicKey getAddress();

    public static IAmPre of(final IdentityPublicKey address) {
        return new AutoValue_IAmPre(address);
    }
}
