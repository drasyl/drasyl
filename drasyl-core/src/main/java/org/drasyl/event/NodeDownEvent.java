package org.drasyl.event;

/**
 * This event signals that the node is shut down.
 * <p>
 * This is an immutable object.
 */
public class NodeDownEvent extends AbstractNodeEvent {
    public NodeDownEvent(final Node node) {
        super(node);
    }

    @Override
    public String toString() {
        return "NodeDownEvent{" +
                "node=" + node +
                '}';
    }
}