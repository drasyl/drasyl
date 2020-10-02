package org.drasyl.event;

/**
 * This event signals that the node is currently not connected to a super peer.
 * <p>
 * This is an immutable object.
 */
public class NodeOfflineEvent extends AbstractNodeEvent {
    public NodeOfflineEvent(final Node node) {
        super(node);
    }

    @Override
    public String toString() {
        return "NodeOfflineEvent{" +
                "node=" + node +
                '}';
    }
}