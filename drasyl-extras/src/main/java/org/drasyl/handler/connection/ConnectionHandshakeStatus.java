/*
 * Copyright (c) 2020-2023 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.handler.connection;

import org.drasyl.util.internal.UnstableApi;

import java.util.Objects;

import static java.util.Objects.requireNonNull;

@UnstableApi
public class ConnectionHandshakeStatus implements ConnectionEvent {
    private final State state;
    private final TransmissionControlBlock tcb;

    ConnectionHandshakeStatus(final State state, final TransmissionControlBlock tcb) {
        this.state = requireNonNull(state);
        this.tcb = tcb;
    }

    public State state() {
        return state;
    }

    @SuppressWarnings("unused")
    public TransmissionControlBlock tcb() {
        return tcb;
    }

    @Override
    public String toString() {
        return "ConnectionHandshakeStatus{" +
                "state=" + state +
                ", tcb=" + tcb +
                '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ConnectionHandshakeStatus that = (ConnectionHandshakeStatus) o;
        return state == that.state && Objects.equals(tcb, that.tcb);
    }

    @Override
    public int hashCode() {
        return Objects.hash(state, tcb);
    }
}
