package org.drasyl.handler.dht.chord.message;

import com.google.auto.value.AutoValue;

/**
 * Offers recipient to add us as predecessor.
 *
 * @see Notified
 */
@SuppressWarnings("java:S1118")
@AutoValue
public abstract class IAmPre implements ChordMessage {
    public static IAmPre of() {
        return new AutoValue_IAmPre();
    }
}
