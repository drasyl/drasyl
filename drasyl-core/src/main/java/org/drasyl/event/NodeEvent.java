package org.drasyl.event;

/**
 * Events that refer to a node.
 */
public interface NodeEvent extends Event {
    Node getNode();
}
