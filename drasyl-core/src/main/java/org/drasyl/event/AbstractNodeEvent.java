package org.drasyl.event;

import java.util.Objects;

import static java.util.Objects.requireNonNull;

public abstract class AbstractNodeEvent implements NodeEvent {
    protected final Node node;

    protected AbstractNodeEvent(Node node) {
        this.node = requireNonNull(node);
    }

    @Override
    public Node getNode() {
        return node;
    }

    @Override
    public int hashCode() {
        return Objects.hash(node);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AbstractNodeEvent that = (AbstractNodeEvent) o;
        return Objects.equals(node, that.node);
    }
}
