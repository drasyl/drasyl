package org.drasyl.handler.dht.chord.message;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class YourSuccessor implements ChordMessage {
    public static YourSuccessor of() {
        return new AutoValue_YourSuccessor();
    }
}
