package org.drasyl.handler.dht.chord.message;

import com.google.auto.value.AutoValue;
import org.drasyl.identity.DrasylAddress;

/**
 * Reply sent in response to {@link YourSuccessor} including my predecessor.
 *
 * @see YourSuccessor
 */
@AutoValue
public abstract class MySuccessor implements ChordMessage {
    public abstract DrasylAddress getAddress();

    public static MySuccessor of(final DrasylAddress address) {
        return new AutoValue_MySuccessor(address);
    }
}
