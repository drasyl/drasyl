package org.drasyl.handler.dht.chord.message;

import com.google.auto.value.AutoValue;
import org.drasyl.identity.DrasylAddress;

/**
 * Reply sent in response to {@link YourPredecessor} including my predecessor.
 *
 * @see YourPredecessor
 */
@AutoValue
public abstract class MyPredecessor implements ChordMessage {
    public abstract DrasylAddress getAddress();

    public static MyPredecessor of(final DrasylAddress address) {
        return new AutoValue_MyPredecessor(address);
    }
}
