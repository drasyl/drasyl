package org.drasyl.handler.membership.cyclon;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

public class CyclonShuffleResponse implements CyclonMessage {
    private final Set<CyclonNeighbor> neighbors;

    private CyclonShuffleResponse(final Set<CyclonNeighbor> neighbors) {
        this.neighbors = requireNonNull(neighbors);
    }

    private CyclonShuffleResponse(final CyclonNeighbor... neighbors) {
        this(Set.of(neighbors));
    }

    @Override
    public Set<CyclonNeighbor> getNeighbors() {
        return Set.copyOf(neighbors);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final CyclonShuffleResponse that = (CyclonShuffleResponse) o;
        return Objects.equals(neighbors, that.neighbors);
    }

    @Override
    public int hashCode() {
        return Objects.hash(neighbors);
    }

    @Override
    public String toString() {
        return "CyclonShuffleResponse{\n" +
                neighbors.stream().map(Object::toString).collect(Collectors.joining(",\n\t", "\t", "\n")) +
                '}';
    }

    public static CyclonShuffleResponse of(final Set<CyclonNeighbor> neighbors) {
        return new CyclonShuffleResponse(neighbors);
    }

    public static CyclonShuffleResponse of(final CyclonNeighbor... neighbors) {
        return new CyclonShuffleResponse(neighbors);
    }
}
