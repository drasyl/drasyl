package org.drasyl.handler.dht.chord.message;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class Alive implements ChordMessage {
    public static Alive of() {
        return new AutoValue_Alive();
    }
}
