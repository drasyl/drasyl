/*
 * Copyright (c) 2020-2021 Heiko Bornholdt and Kevin Röbert
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.drasyl.event;

import org.drasyl.annotation.NonNull;

import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * This events signals that the node encountered an unrecoverable error.
 * <p>
 * This is an immutable object.
 */
public class NodeUnrecoverableErrorEvent extends AbstractNodeEvent {
    private final Throwable error;

    /**
     * @throws NullPointerException if {@code node} or {@code error} is {@code null}
     * @deprecated Use {@link #of(Node, Throwable)} instead.
     */
    // make method private on next release
    @Deprecated(since = "0.4.0", forRemoval = true)
    public NodeUnrecoverableErrorEvent(final Node node, final Throwable error) {
        super(node);
        this.error = requireNonNull(error);
    }

    /**
     * Returns the exception that crashed the node.
     *
     * @return the exception that crashed the node
     */
    @NonNull
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
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final NodeUnrecoverableErrorEvent that = (NodeUnrecoverableErrorEvent) o;
        return Objects.equals(error, that.error);
    }

    /**
     * @throws NullPointerException if {@code node} or {@code error} is {@code null}
     */
    public static NodeUnrecoverableErrorEvent of(final Node node, final Throwable error) {
        return new NodeUnrecoverableErrorEvent(node, error);
    }
}
