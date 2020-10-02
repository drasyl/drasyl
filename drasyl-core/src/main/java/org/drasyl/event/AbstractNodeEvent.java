package org.drasyl.event;

import java.util.Objects;

import static java.util.Objects.requireNonNull;

abstract class AbstractNodeEvent implements NodeEvent {
    protected final Node node;

    protected AbstractNodeEvent(final Node node) {
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
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AbstractNodeEvent that = (AbstractNodeEvent) o;
        return Objects.equals(node, that.node);
    }
}