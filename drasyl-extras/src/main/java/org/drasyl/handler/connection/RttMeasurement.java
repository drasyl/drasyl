/*
 * Copyright (c) 2020-2022 Heiko Bornholdt and Kevin Röbert
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

import static org.drasyl.handler.connection.ConnectionHandshakeHandler.SEQ_NO_SPACE;
import static org.drasyl.util.SerialNumberArithmetic.add;
import static org.drasyl.util.SerialNumberArithmetic.lessThan;
import static org.drasyl.util.SerialNumberArithmetic.lessThanOrEqualTo;

// https://www.rfc-editor.org/rfc/rfc7323
// Chapter 4
public class RttMeasurement {
    // RFC 1323: Round-Trip Time Measurement
    long tsRecent; // holds a timestamp to be echoed in TSecr whenever a segment is sent
    long lastAckSent; // holds the ACK field from the last segment sent

    public void segmentArrives(final ConnectionHandshakeSegment seg) {
        if (lessThanOrEqualTo(seg.seq(), lastAckSent, SEQ_NO_SPACE) && lessThan(lastAckSent, add(seg.seq(), seg.len(), SEQ_NO_SPACE), SEQ_NO_SPACE)) {
            tsRecent = seg.tsVal();
        }
    }

    public void sendAck(final ConnectionHandshakeSegment seg) {
        seg.setTsVal(System.nanoTime() / 1_000_000);
        seg.setTsEcr(tsRecent);
        lastAckSent = seg.ack();
    }
}
