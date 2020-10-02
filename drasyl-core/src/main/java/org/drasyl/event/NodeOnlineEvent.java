package org.drasyl.event;

/**
 * This event signals that the node has successfully registered with the super peer. If a node has
 * been configured with no super peer (e.g. if it is a root node), the event is immediately
 * emitted.
 * <p>
 * This is an immutable object.
 */
public class NodeOnlineEvent extends AbstractNodeEvent {
    public NodeOnlineEvent(final Node node) {
        super(node);
    }

    @Override
    public String toString() {
        return "NodeOnlineEvent{" +
                "node=" + node +
                '}';
    }
}