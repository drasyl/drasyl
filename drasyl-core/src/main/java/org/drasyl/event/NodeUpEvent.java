package org.drasyl.event;

/**
 * This event signals that the node has been started.
 */
public class NodeUpEvent extends AbstractNodeEvent {
    public NodeUpEvent(Node node) {
        super(node);
    }

    @Override
    public String toString() {
        return "NodeUpEvent{" +
                "node=" + node +
                '}';
    }
}