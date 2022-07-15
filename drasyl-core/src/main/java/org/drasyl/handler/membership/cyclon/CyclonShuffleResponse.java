package org.drasyl.handler.membership.cyclon;

import com.google.auto.value.AutoValue;

import java.util.Set;

@AutoValue
@SuppressWarnings("java:S1118")
public abstract class CyclonShuffleResponse implements CyclonMessage {
    public static CyclonShuffleResponse of(final Set<CyclonNeighbor> neighbors) {
        return new AutoValue_CyclonShuffleResponse(neighbors);
    }
}
