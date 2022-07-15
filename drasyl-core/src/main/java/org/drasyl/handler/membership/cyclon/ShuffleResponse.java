package org.drasyl.handler.membership.cyclon;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

public class ShuffleResponse implements CyclonMessage {
    private final Set<CyclonNeighbor> neighbors;

    private ShuffleResponse(final Set<CyclonNeighbor> neighbors) {
        this.neighbors = requireNonNull(neighbors);
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
        final ShuffleResponse that = (ShuffleResponse) o;
        return Objects.equals(neighbors, that.neighbors);
    }

    @Override
    public int hashCode() {
        return Objects.hash(neighbors);
    }

    @Override
    public String toString() {
        return "ShuffleResponse{\n" +
                neighbors.stream().map(Object::toString).collect(Collectors.joining(",\n\t", "\t", "\n")) +
                '}';
    }

    @JsonCreator
    public static ShuffleResponse of(@JsonProperty("neighbors") final Set<CyclonNeighbor> neighbors) {
        return new ShuffleResponse(neighbors);
    }
}
