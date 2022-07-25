package org.drasyl.handler.dht.chord.message;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class Notified implements ChordMessage {
    public static Notified of() {
        return new AutoValue_Notified();
    }
}
