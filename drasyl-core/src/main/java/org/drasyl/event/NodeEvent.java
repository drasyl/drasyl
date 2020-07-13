package org.drasyl.event;

/**
 * Events that refer to a {@link Node}.
 */
public interface NodeEvent extends Event {
    Node getNode();
}
