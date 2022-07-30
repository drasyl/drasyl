package org.drasyl.handler.dht.chord;

import com.google.auto.value.AutoValue;
import org.drasyl.identity.DrasylAddress;

/**
 * Reply sent in response to {@link ChordLookup} including the closest node for a given {@code id}.
 *
 * @see ChordLookup
 */
@SuppressWarnings("java:S1118")
@AutoValue
public abstract class ChordResponse {
    public abstract long getId();

    public abstract DrasylAddress getAddress();

    public static ChordResponse of(final long id, final DrasylAddress address) {
        return new AutoValue_ChordResponse(id, address);
    }
}
