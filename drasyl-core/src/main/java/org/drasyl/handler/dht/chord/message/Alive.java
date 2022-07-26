package org.drasyl.handler.dht.chord.message;

import com.google.auto.value.AutoValue;

/**
 * Reply sent in response to {@link Keep} to indicate we're still alive.
 *
 * @see Keep
 */
@SuppressWarnings("java:S1118")
@AutoValue
public abstract class Alive implements ChordMessage {
    public static Alive of() {
        return new AutoValue_Alive();
    }
}
