package org.drasyl.handler.dht.chord;

import com.google.auto.value.AutoValue;
import org.drasyl.identity.DrasylAddress;

import static org.drasyl.util.Preconditions.requireInRange;

/**
 * Triggers a lookup in the DHT ring to find the closest node for given {@code id}.
 *
 * @see ChordLookup
 */
@SuppressWarnings("java:S1118")
@AutoValue
public abstract class ChordLookup {
    public static final long MAX_ID = (long) Math.pow(2, 32);

    public abstract DrasylAddress getContact();

    public abstract long getId();

    public static ChordLookup of(final DrasylAddress contact, final long id) {
        return new AutoValue_ChordLookup(contact, requireInRange(id, 0, MAX_ID));
    }
}
