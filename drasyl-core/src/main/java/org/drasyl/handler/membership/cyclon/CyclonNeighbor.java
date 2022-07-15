package org.drasyl.handler.membership.cyclon;

import com.google.auto.value.AutoValue;
import org.drasyl.identity.DrasylAddress;

@AutoValue
public abstract class CyclonNeighbor {
    public abstract DrasylAddress getAddress();

    public abstract int getAge();

    public CyclonNeighbor increaseAge() {
        // FIXME: check for overflow
        return CyclonNeighbor.of(getAddress(), getAge() + 1);
    }

    public static CyclonNeighbor of(final DrasylAddress address, final int age) {
        return new AutoValue_CyclonNeighbor(address, age);
    }

    public static CyclonNeighbor of(final DrasylAddress address) {
        return of(address, 0);
    }
}
