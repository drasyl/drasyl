package org.drasyl.handler.dht.chord.message;

import com.google.auto.value.AutoValue;

/**
 * Reply sent in response to {@link IAmPre} acknowledging the receival.
 *
 * @see IAmPre
 */
@SuppressWarnings("java:S1118")
@AutoValue
public abstract class Notified implements ChordMessage {
    public static Notified of() {
        return new AutoValue_Notified();
    }
}
