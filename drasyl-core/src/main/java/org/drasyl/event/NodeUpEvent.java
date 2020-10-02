package org.drasyl.event;

/**
 * This event signals that the node has been started.
 * <p>
 * This is an immutable object.
 */
public class NodeUpEvent extends AbstractNodeEvent {
    public NodeUpEvent(final Node node) {
        super(node);
    }

    @Override
    public String toString() {
        return "NodeUpEvent{" +
                "node=" + node +
                '}';
    }
}