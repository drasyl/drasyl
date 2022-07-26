package org.drasyl.handler.dht.chord.message;

import com.google.auto.value.AutoValue;

/**
 * Reply sent in response to {@link YourPredecessor} indicating that we do not have a predecessor.
 *
 * @see YourPredecessor
 */
@SuppressWarnings("java:S1118")
@AutoValue
public abstract class NothingPredecessor implements ChordMessage {
    public static NothingPredecessor of() {
        return new AutoValue_NothingPredecessor();
    }
}
