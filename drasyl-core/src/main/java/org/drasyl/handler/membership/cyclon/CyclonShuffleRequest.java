package org.drasyl.handler.membership.cyclon;

import com.google.auto.value.AutoValue;

import java.util.Set;

@AutoValue
@SuppressWarnings("java:S1118")
public abstract class CyclonShuffleRequest implements CyclonMessage {
    public static CyclonShuffleRequest of(final Set<CyclonNeighbor> neighbors) {
        return new AutoValue_CyclonShuffleRequest(neighbors);
    }
}
