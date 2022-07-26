package org.drasyl.handler.dht.chord.message;

import com.google.auto.value.AutoValue;

/**
 * Check if peer is still alive.
 *
 * @see Alive
 */
@SuppressWarnings("java:S1118")
@AutoValue
public abstract class Keep implements ChordMessage {
    public static Keep of() {
        return new AutoValue_Keep();
    }
}
