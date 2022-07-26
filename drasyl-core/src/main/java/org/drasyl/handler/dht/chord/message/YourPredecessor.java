package org.drasyl.handler.dht.chord.message;

import com.google.auto.value.AutoValue;

/**
 * Asks recipient for its predecessor.
 *
 * @see MyPredecessor
 * @see NothingPredecessor
 */
@SuppressWarnings("java:S1118")
@AutoValue
public abstract class YourPredecessor implements ChordMessage {
    public static YourPredecessor of() {
        return new AutoValue_YourPredecessor();
    }
}
