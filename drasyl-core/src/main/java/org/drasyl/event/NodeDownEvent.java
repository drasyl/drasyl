package org.drasyl.event;

/**
 * This event signals that the node is shut down.
 */
public class NodeDownEvent extends AbstractNodeEvent {
    public NodeDownEvent(Node node) {
        super(node);
    }

    @Override
    public String toString() {
        return "NodeDownEvent{" +
                "node=" + node +
                '}';
    }
}