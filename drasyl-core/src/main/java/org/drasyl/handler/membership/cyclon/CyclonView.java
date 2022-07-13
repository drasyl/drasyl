package org.drasyl.handler.membership.cyclon;

import com.google.auto.value.AutoValue;
import org.drasyl.identity.DrasylAddress;

import java.util.Set;

@AutoValue
public abstract class CyclonView {
    public abstract int getCapacity();
    public abstract Set<CyclonNeighbor> getNeighbors();

    public static CyclonView of(final int capacity, final Set<CyclonNeighbor> neighbors) {
        return new AutoValue_CyclonView(capacity, neighbors);
    }
}
