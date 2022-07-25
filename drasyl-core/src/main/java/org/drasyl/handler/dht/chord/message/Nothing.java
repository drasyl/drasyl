package org.drasyl.handler.dht.chord.message;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class Nothing implements ChordMessage {
    public static Nothing of() {
        return new AutoValue_Nothing();
    }
}
