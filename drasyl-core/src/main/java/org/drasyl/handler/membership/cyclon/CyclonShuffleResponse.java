package org.drasyl.handler.membership.cyclon;

import com.google.auto.value.AutoValue;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Response of a CYCLON shuffle request.
 *
 * @see CyclonShuffleRequest
 */
@AutoValue
public abstract class CyclonShuffleResponse implements CyclonMessage {
    @Override
    public String toString() {
        return "CyclonShuffleResponse{\n" +
                getNeighbors().stream().map(Object::toString).collect(Collectors.joining(",\n\t", "\t", "\n")) +
                '}';
    }

    public static CyclonShuffleResponse of(final Set<CyclonNeighbor> neighbors) {
        return new AutoValue_CyclonShuffleResponse(neighbors);
    }

    public static CyclonShuffleResponse of(final CyclonNeighbor... neighbors) {
        return new AutoValue_CyclonShuffleResponse(Set.of(neighbors));
    }
}
