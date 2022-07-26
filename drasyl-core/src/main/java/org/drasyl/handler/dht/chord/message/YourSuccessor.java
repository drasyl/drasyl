package org.drasyl.handler.dht.chord.message;

import com.google.auto.value.AutoValue;

/**
 * Asks recipient for its predecessor.
 *
 * @see MySuccessor
 * @see NothingSuccessor
 */
@SuppressWarnings("java:S1118")
@AutoValue
public abstract class YourSuccessor implements ChordMessage {
    public static YourSuccessor of() {
        return new AutoValue_YourSuccessor();
    }
}
