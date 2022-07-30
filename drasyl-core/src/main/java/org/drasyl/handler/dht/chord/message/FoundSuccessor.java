package org.drasyl.handler.dht.chord.message;

import com.google.auto.value.AutoValue;
import org.drasyl.identity.DrasylAddress;

/**
 * Reply sent in response to {@link FindSuccessor} including the successor for a given {@code id}.
 *
 * @see FindSuccessor
 */
@AutoValue
public abstract class FoundSuccessor implements ChordMessage {
    public abstract DrasylAddress getAddress();

    public static FoundSuccessor of(final DrasylAddress address) {
        return new AutoValue_FoundSuccessor(address);
    }
}
