package org.drasyl.event;

import java.util.Objects;

/**
 * This events signals that the node encountered an unrecoverable error.
 * <p>
 * This is an immutable object.
 */
public class NodeUnrecoverableErrorEvent extends AbstractNodeEvent {
    private final Throwable error;

    public NodeUnrecoverableErrorEvent(Node node, Throwable error) {
        super(node);
        this.error = error;
    }

    public Throwable getError() {
        return error;
    }

    @Override
    public String toString() {
        return "NodeUnrecoverableErrorEvent{" +
                "error=" + error +
                ", node=" + node +
                '}';
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), error);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        NodeUnrecoverableErrorEvent that = (NodeUnrecoverableErrorEvent) o;
        return Objects.equals(error, that.error);
    }
}