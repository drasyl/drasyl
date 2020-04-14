package org.drasyl.core.models;

public class Node extends AbstractNode {
    public Node(Identity address) {
        super(address);
    }

    public static Node of(Identity address) {
        return new Node(address);
    }

    @Override
    public String toString() {
        return "Node{" +
                "address=" + getAddress() +
                '}';
    }
}
