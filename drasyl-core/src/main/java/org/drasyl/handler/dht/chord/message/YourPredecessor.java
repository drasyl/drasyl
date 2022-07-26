package org.drasyl.handler.dht.chord.message;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class YourPredecessor implements ChordMessage {
    public static YourPredecessor of() {
        return new AutoValue_YourPredecessor();
    }
}
