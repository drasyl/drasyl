package org.drasyl.handler.dht.chord.message;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class NothingPredecessor implements ChordMessage {
    public static NothingPredecessor of() {
        return new AutoValue_NothingPredecessor();
    }
}
