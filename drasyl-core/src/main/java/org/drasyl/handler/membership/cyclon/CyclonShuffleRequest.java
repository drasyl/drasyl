package org.drasyl.handler.membership.cyclon;

import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

public class CyclonShuffleRequest implements CyclonMessage {
    private final Set<CyclonNeighbor> neighbors;

    private CyclonShuffleRequest(final Set<CyclonNeighbor> neighbors) {
        this.neighbors = requireNonNull(neighbors);
    }

    private CyclonShuffleRequest(final CyclonNeighbor... neighbors) {
        this(Set.of(neighbors));
    }

    @Override
    public Set<CyclonNeighbor> getNeighbors() {
        return Set.copyOf(neighbors);
    }

    @Override
    public String toString() {
        return "CyclonShuffleRequest{\n" +
                neighbors.stream().map(Object::toString).collect(Collectors.joining(",\n\t", "\t", "\n")) +
                '}';
    }

    public static CyclonShuffleRequest of(final Set<CyclonNeighbor> neighbors) {
        return new CyclonShuffleRequest(neighbors);
    }

    public static CyclonShuffleRequest of(final CyclonNeighbor... neighbors) {
        return new CyclonShuffleRequest(neighbors);
    }
}
