package org.drasyl.handler.dht.chord.message;

import com.google.auto.value.AutoValue;

/**
 * Reply sent in response to {@link YourSuccessor} indicating that we do not have a successor.
 *
 * @see YourSuccessor
 */
@SuppressWarnings("java:S1118")
@AutoValue
public abstract class NothingSuccessor implements ChordMessage {
    public static NothingSuccessor of() {
        return new AutoValue_NothingSuccessor();
    }
}
