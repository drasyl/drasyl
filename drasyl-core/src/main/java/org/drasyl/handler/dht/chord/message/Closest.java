package org.drasyl.handler.dht.chord.message;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class Closest implements ChordMessage {
    public abstract long getId();

    public static Closest of(final long id) {
        return new AutoValue_Closest(id);
    }
}
