package org.drasyl.handler.membership.cyclon;

import java.util.Set;

/**
 * Interface to denote messages used by CYCLON.
 *
 * @see <a href="https://doi.org/10.1007/s10922-005-4441-x">CYCLON: Inexpensive Membership
 * Management for Unstructured P2P Overlays</a>
 */
public interface CyclonMessage {
    Set<CyclonNeighbor> getNeighbors();
}
