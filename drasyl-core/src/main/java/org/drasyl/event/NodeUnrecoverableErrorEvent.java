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

import com.google.auto.value.AutoValue;

/**
 * This events signals that the node encountered an unrecoverable error.
 * <p>
 * This is an immutable object.
 *
 * @see NodeUpEvent
 * @see NodeDownEvent
 * @see NodeNormalTerminationEvent
 */
@AutoValue
@SuppressWarnings("java:S118")
public abstract class NodeUnrecoverableErrorEvent implements NodeEvent {
    /**
     * Returns the exception that crashed the node.
     *
     * @return the exception that crashed the node
     */
    public abstract Throwable getError();

    /**
     * @throws NullPointerException if {@code node} or {@code error} is {@code null}
     */
    public static NodeUnrecoverableErrorEvent of(final Node node, final Throwable error) {
        return new AutoValue_NodeUnrecoverableErrorEvent(node, error);
    }
}
