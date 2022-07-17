package org.drasyl.handler.membership.cyclon;

import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.InconsistentSortedSet;
import org.drasyl.util.Pair;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.stream.Collectors;

import static java.util.Collections.reverseOrder;
import static org.drasyl.util.SetUtil.firstElements;

/**
 * Node's partial view of the entire network.
 */
public class CyclonView {
    private static final Logger LOG = LoggerFactory.getLogger(CyclonView.class);
    private final int viewSize;
    private final SortedSet<CyclonNeighbor> neighbors;

    /**
     * @param viewSize  max. cache slots (denoted as <i>c</i> in the paper)
     * @param neighbors
     */
    private CyclonView(final int viewSize, final List<CyclonNeighbor> neighbors) {
        if (viewSize < 1) {
            throw new IllegalArgumentException("viewSize (c) must be greater than or equal to 1.");
        }
        this.viewSize = viewSize;
        LOG.debug("viewSize (c) = {}", this.viewSize);
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
        final Set<CyclonNeighbor> receivedNeighbors = firstElements(fullReceivedNeighbors, viewSize);

        // do we need to replace?
        // replace sent neighbors first (replaceCandidates)
        int replaceCount = Math.max(neighbors.size() + receivedNeighbors.size() - viewSize, 0);
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

    public int viewSize() {
        return viewSize;
    }

    public boolean remove(final CyclonNeighbor neighbor) {
        return neighbors.remove(neighbor);
    }

    public boolean add(final CyclonNeighbor neighbor) {
        return neighbors.add(neighbor);
    }

    public static CyclonView of(final int viewSize, final List<CyclonNeighbor> neighbors) {
        return new CyclonView(viewSize, neighbors);
    }

    public static CyclonView ofKeys(final int viewSize, final List<DrasylAddress> neighbors) {
        return of(viewSize, neighbors.stream().map(CyclonNeighbor::of).collect(Collectors.toList()));
    }
}
