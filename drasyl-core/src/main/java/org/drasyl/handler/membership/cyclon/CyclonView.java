package org.drasyl.handler.membership.cyclon;

import com.google.auto.value.AutoValue;
import org.drasyl.util.Pair;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@AutoValue
public abstract class CyclonView {
    public abstract int getCapacity();

    public abstract Set<CyclonNeighbor> getNeighbors();

    public boolean isEmpty() {
        return getNeighbors().isEmpty();
    }

    public int size() {
        return getNeighbors().size();
    }

    public CyclonView increaseAge() {
        return CyclonView.of(getCapacity(), getNeighbors().stream().map(CyclonNeighbor::increaseAge).collect(Collectors.toSet()));
    }

    public Pair<CyclonNeighbor, Set<CyclonNeighbor>> highestAgeAndOtherRandomNeighbors(final int n) {
        final CyclonNeighbor highestAge = getNeighbors().stream().max((a, b) -> Integer.compare(b.getAge(), a.getAge())).get();

        final List<CyclonNeighbor> list = getNeighbors().stream().filter(neighbor -> !highestAge.equals(neighbor)).collect(Collectors.toCollection(LinkedList::new));
        Collections.shuffle(list);
        final Set<CyclonNeighbor> otherRandomNeighbors = new HashSet<>(list.subList(0, Math.min(list.size(), n)));

        return Pair.of(highestAge, otherRandomNeighbors);
    }


    public static CyclonView of(final int capacity, final Set<CyclonNeighbor> neighbors) {
        return new AutoValue_CyclonView(capacity, neighbors);
    }
}
