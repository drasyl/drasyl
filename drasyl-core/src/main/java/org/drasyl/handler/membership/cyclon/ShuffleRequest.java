package org.drasyl.handler.membership.cyclon;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

public class ShuffleRequest implements CyclonMessage {
    private final Set<CyclonNeighbor> neighbors;

    private ShuffleRequest(final Set<CyclonNeighbor> neighbors) {
        this.neighbors = requireNonNull(neighbors);
    }

    @Override
    public Set<CyclonNeighbor> getNeighbors() {
        return Set.copyOf(neighbors);
    }

    @Override
    public String toString() {
        return "ShuffleRequest{\n" +
                neighbors.stream().map(Object::toString).collect(Collectors.joining(",\n\t", "\t", "\n")) +
                '}';
    }

    @JsonCreator
    public static ShuffleRequest of(@JsonProperty("neighbors") final Set<CyclonNeighbor> neighbors) {
        return new ShuffleRequest(neighbors);
    }
}
