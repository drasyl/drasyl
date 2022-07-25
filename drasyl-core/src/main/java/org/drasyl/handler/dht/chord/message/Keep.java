package org.drasyl.handler.dht.chord.message;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class Keep implements ChordMessage {
    public static Keep of() {
        return new AutoValue_Keep();
    }
}
