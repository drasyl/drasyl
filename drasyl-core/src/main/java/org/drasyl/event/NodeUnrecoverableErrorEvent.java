package org.drasyl.event;

/**
 * This events signals that the node encountered an unrecoverable error.
 */
public class NodeUnrecoverableErrorEvent extends AbstractNodeEvent {
    public NodeUnrecoverableErrorEvent(Node node) {
        super(node);
    }

    @Override
    public String toString() {
        return "NodeUnrecoverableErrorEvent{" +
                "node=" + node +
                '}';
    }
}
