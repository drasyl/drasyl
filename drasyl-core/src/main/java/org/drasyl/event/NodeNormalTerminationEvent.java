package org.drasyl.event;

/**
 * This events signals that the node has terminated normally.
 */
public class NodeNormalTerminationEvent extends AbstractNodeEvent {
    public NodeNormalTerminationEvent(Node node) {
        super(node);
    }

    @Override
    public String toString() {
        return "NodeNormalTerminationEvent{" +
                "node=" + node +
                '}';
    }
}
