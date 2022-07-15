package org.drasyl.handler.membership.cyclon;

import org.drasyl.util.Pair;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static java.lang.Math.min;
import static java.util.Objects.requireNonNull;

/**
 * Node's partial view of the entire network.
 */
public class CyclonView {
    private static final Logger LOG = LoggerFactory.getLogger(CyclonView.class);
    private final int viewSize;
    private final SortedList<CyclonNeighbor> neighbors;

    /**
     * @param viewSize  max. cache slots (denoted as <i>c</i> in the paper)
     * @param neighbors
     */
    public CyclonView(final int viewSize, final SortedList<CyclonNeighbor> neighbors) {
        if (viewSize < 1) {
            throw new IllegalArgumentException("viewSize (c) must be greater than or equal to 1.");
        }
        this.viewSize = viewSize;
        LOG.debug("viewSize (c) = {}", this.viewSize);
        this.neighbors = requireNonNull(neighbors);
    }

    /**
     * @param viewSize max. cache slots (denoted as <i>c</i> in the paper)
     */
    public CyclonView(final int viewSize) {
        this(viewSize, new SortedList<>());
    }

    @Override
    public String toString() {
        return "View{\n" +
                neighbors.stream().map(Object::toString).collect(Collectors.joining(",\n\t", "\t", "\n")) +
                '}';
    }

    public boolean isEmpty() {
        return neighbors.isEmpty();
    }

    public void increaseAge() {
        neighbors.forEach(CyclonNeighbor::increaseAgeByOne);
    }

    public Pair<CyclonNeighbor, Set<CyclonNeighbor>> highestAgeAndOtherRandomNeighbors(final int n) {
        final CyclonNeighbor highestAge = neighbors.getLast();

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
        return Set.copyOf(this.neighbors);
    }

    public void update(final Set<CyclonNeighbor> receivedNeighbors,
                       final Set<CyclonNeighbor> replaceCandidates) {
        update(List.copyOf(receivedNeighbors), List.copyOf(replaceCandidates));
    }

    void update(List<CyclonNeighbor> receivedNeighbors,
                final List<CyclonNeighbor> replaceCandidates) {
        // just pick up to viewSize received neighbors
        receivedNeighbors = receivedNeighbors.subList(0, min(receivedNeighbors.size(), viewSize));

        // do we need to replace?
        // replacing entries among the ones sent (replaceCandidates)
        final int size = this.neighbors.size();
        int replaceCount = Math.max(size + receivedNeighbors.size() - viewSize, 0);
        final SortedList<CyclonNeighbor> sortedReplaceCandidates = new SortedList<>();
        sortedReplaceCandidates.addAll(replaceCandidates);
        for (int i = sortedReplaceCandidates.size(); i-- > 0; ) {
            final CyclonNeighbor neighbor = sortedReplaceCandidates.get(i);
            if (replaceCount == 0) {
                break;
            }
            if (this.neighbors.remove(neighbor)) {
                replaceCount--;
            }
        }

        // do we need still replace?
        // kick oldest
        while (replaceCount > 0) {
            final CyclonNeighbor neighbor = this.neighbors.getLast();
            if (this.neighbors.remove(neighbor)) {
                replaceCount--;
            }
        }

        // use empty cache slots (if any)
        this.neighbors.addAll(receivedNeighbors);
    }

    public int viewSize() {
        return viewSize;
    }

    public boolean remove(final CyclonNeighbor neighbor) {
        return neighbors.remove(neighbor);
    }

    public boolean add(final CyclonNeighbor neighbor) {
        return neighbors.add(neighbor);
    }
}
