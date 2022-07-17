package org.drasyl.handler.membership.cyclon;

import java.util.Set;

/**
 * Interface to denote messages used by CYCLON.
 */
public interface CyclonMessage {
    Set<CyclonNeighbor> getNeighbors();
}
