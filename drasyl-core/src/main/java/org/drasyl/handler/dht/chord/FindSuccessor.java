package org.drasyl.handler.dht.chord;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class FindSuccessor implements ChordMessage {
    public abstract long getId();

    public static FindSuccessor of(final long id) {
        return new AutoValue_FindSuccessor(id);
    }
}
