package org.drasyl.handler.dht.chord.message;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class NothingSuccessor implements ChordMessage {
    public static NothingSuccessor of() {
        return new AutoValue_NothingSuccessor();
    }
}
