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
package org.drasyl.handler.arq.gobackn;

import org.drasyl.util.UnsignedInteger;

import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * Ack message of the Go-Back-N ARQ protocol.
 */
@Deprecated
public class GoBackNArqAck implements GoBackNArqMessage {
    private final UnsignedInteger sequenceNo;

    public GoBackNArqAck(final UnsignedInteger sequenceNo) {
        this.sequenceNo = requireNonNull(sequenceNo);
    }

    @Override
    public UnsignedInteger sequenceNo() {
        return sequenceNo;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final GoBackNArqAck that = (GoBackNArqAck) o;
        return Objects.equals(sequenceNo, that.sequenceNo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sequenceNo);
    }

    @Override
    public String toString() {
        return "GoBackNArqAck{" +
                "sequenceNo=" + sequenceNo +
                '}';
    }
}
