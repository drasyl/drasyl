package org.drasyl.event;

/**
 * This events signals that the identity is already being used by another node on the network.
 */
public class NodeIdentityCollisionEvent extends AbstractNodeEvent {
    protected NodeIdentityCollisionEvent(Node node) {
        super(node);
    }

    @Override
    public String toString() {
        return "NodeIdentityCollisionEvent{" +
                "node=" + node +
                '}';
    }
}