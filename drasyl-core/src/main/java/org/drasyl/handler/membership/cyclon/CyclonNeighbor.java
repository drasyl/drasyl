package org.drasyl.handler.membership.cyclon;

import com.google.auto.value.AutoValue;
import org.drasyl.identity.DrasylAddress;

@AutoValue
public abstract class CyclonNeighbor {
    public abstract DrasylAddress getAddress();
    public abstract int getAge();

    public static CyclonNeighbor of(final DrasylAddress address, final int age) {
        return new AutoValue_CyclonNeighbor(address, age);
    }
}
