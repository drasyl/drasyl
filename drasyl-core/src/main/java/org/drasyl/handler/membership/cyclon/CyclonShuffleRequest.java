package org.drasyl.handler.membership.cyclon;

import com.google.auto.value.AutoValue;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Requests a CYCLON shuffle.
 *
 * @see CyclonShuffleResponse
 */
@AutoValue
public abstract class CyclonShuffleRequest implements CyclonMessage {
    @Override
    public String toString() {
        return "CyclonShuffleRequest{\n" +
                getNeighbors().stream().map(Object::toString).collect(Collectors.joining(",\n\t", "\t", "\n")) +
                '}';
    }

    public static CyclonShuffleRequest of(final Set<CyclonNeighbor> neighbors) {
        return new AutoValue_CyclonShuffleRequest(neighbors);
    }

    public static CyclonShuffleRequest of(final CyclonNeighbor... neighbors) {
        return new AutoValue_CyclonShuffleRequest(Set.of(neighbors));
    }
}
