/*
 * Copyright (c) 2020-2022 Heiko Bornholdt and Kevin Röbert
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.drasyl.handler.membership.cyclon;

import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.InconsistentSortedSet;
import org.drasyl.util.Pair;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.stream.Collectors;

import static java.util.Collections.reverseOrder;
import static org.drasyl.util.Preconditions.requirePositive;
import static org.drasyl.util.SetUtil.firstElements;

/**
 * Local peer's (partial) view of the network.
 *
 * @see CyclonNeighbor
 */
public final class CyclonView {
    private final int capacity;
    private final SortedSet<CyclonNeighbor> neighbors;

    /**
     * @param capacity  view capacity (denoted as <i>c</i> in the paper)
     * @param neighbors initial list of neighbors
     */
    private CyclonView(final int capacity, final Set<CyclonNeighbor> neighbors) {
        this.capacity = requirePositive(capacity);
        this.neighbors = new InconsistentSortedSet<>(neighbors);
    }

    @Override
    public String toString() {
        return "CyclonView{\n" +
                neighbors.stream().map(Object::toString).collect(Collectors.joining(",\n\t", "\t", "\n")) +
                '}';
    }

    public boolean isEmpty() {
        return neighbors.isEmpty();
    }

    public void increaseAgeByOne() {
        neighbors.forEach(CyclonNeighbor::increaseAgeByOne);
    }

    public Pair<CyclonNeighbor, Set<CyclonNeighbor>> highestAgeAndOtherRandomNeighbors(final int n) {
        // highest
        final CyclonNeighbor highestAge = neighbors.last();

        // n other random neighbors
        final List<CyclonNeighbor> list = neighbors.stream().filter(neighbor -> !highestAge.equals(neighbor)).collect(Collectors.toCollection(LinkedList::new));
        Collections.shuffle(list);
        final Set<CyclonNeighbor> otherRandomNeighbors = new HashSet<>(list.subList(0, Math.min(list.size(), n)));

        return Pair.of(highestAge, otherRandomNeighbors);
    }

    public Set<CyclonNeighbor> randomNeighbors(final int n) {
        final List<CyclonNeighbor> list = new LinkedList<>(neighbors);
        Collections.shuffle(list);
        return new HashSet<>(list.subList(0, Math.min(list.size(), n)));
    }

    public Set<CyclonNeighbor> getNeighbors() {
        return Set.copyOf(neighbors);
    }

    public void update(final Set<CyclonNeighbor> fullReceivedNeighbors,
                       final Set<CyclonNeighbor> replaceCandidates) {
        // pick up no more than viewSize received neighbors
        final Set<CyclonNeighbor> receivedNeighbors = firstElements(fullReceivedNeighbors, capacity);

        // do we need to replace?
        // replace sent neighbors first (replaceCandidates)
        int replaceCount = Math.max(neighbors.size() + receivedNeighbors.size() - capacity, 0);
        final Set<CyclonNeighbor> sortedReplaceCandidates = new InconsistentSortedSet<>(reverseOrder());
        sortedReplaceCandidates.addAll(replaceCandidates);
        final Iterator<CyclonNeighbor> iterator = sortedReplaceCandidates.iterator();
        while (replaceCount > 0 && iterator.hasNext()) {
            final CyclonNeighbor neighbor = iterator.next();
            if (neighbors.remove(neighbor)) {
                replaceCount--;
            }
        }

        // do we need still replace?
        // remove the oldest neighbors
        while (replaceCount > 0) {
            final CyclonNeighbor neighbor = neighbors.last();
            if (neighbors.remove(neighbor)) {
                replaceCount--;
            }
        }

        // use empty cache slots (if any)
        neighbors.addAll(receivedNeighbors);
    }

    public int capacity() {
        return capacity;
    }

    public boolean remove(final CyclonNeighbor neighbor) {
        return neighbors.remove(neighbor);
    }

    public boolean add(final CyclonNeighbor neighbor) {
        return neighbors.add(neighbor);
    }

    public static CyclonView of(final int capacity, final Set<CyclonNeighbor> neighbors) {
        return new CyclonView(capacity, neighbors);
    }

    public static CyclonView ofKeys(final int capacity, final Collection<DrasylAddress> neighbors) {
        return of(capacity, neighbors.stream().map(CyclonNeighbor::of).collect(Collectors.toSet()));
    }
}
