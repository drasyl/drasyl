package org.drasyl.handler.dht.chord.message;

import com.google.auto.value.AutoValue;
import org.drasyl.identity.IdentityPublicKey;

/**
 * Reply sent in response to {@link YourPredecessor} including my predecessor.
 *
 * @see YourPredecessor
 */
@AutoValue
public abstract class MyPredecessor implements ChordMessage {
    public abstract IdentityPublicKey getAddress();

    public static MyPredecessor of(final IdentityPublicKey address) {
        return new AutoValue_MyPredecessor(address);
    }
}
