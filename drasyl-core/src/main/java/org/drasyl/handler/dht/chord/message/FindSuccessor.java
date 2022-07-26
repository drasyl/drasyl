package org.drasyl.handler.dht.chord.message;

import com.google.auto.value.AutoValue;

/**
 * Asks peer to find successor for a given {@code id}.
 *
 * @see FoundSuccessor
 */
@AutoValue
public abstract class FindSuccessor implements ChordMessage {
    public abstract long getId();

    public static FindSuccessor of(final long id) {
        return new AutoValue_FindSuccessor(id);
    }
}
