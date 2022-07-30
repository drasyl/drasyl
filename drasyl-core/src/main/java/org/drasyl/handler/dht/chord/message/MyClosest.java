package org.drasyl.handler.dht.chord.message;

import com.google.auto.value.AutoValue;
import org.drasyl.identity.DrasylAddress;

/**
 * Reply sent in response to {@link Closest} including the closest finger to a given {@code id}.
 *
 * @see Closest
 */
@AutoValue
public abstract class MyClosest implements ChordMessage {
    public abstract DrasylAddress getAddress();

    public static MyClosest of(final DrasylAddress address) {
        return new AutoValue_MyClosest(address);
    }
}
