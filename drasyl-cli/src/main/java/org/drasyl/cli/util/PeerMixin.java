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
package org.drasyl.cli.util;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.drasyl.handler.peers.Peer;
import org.drasyl.handler.peers.Role;

import java.net.InetSocketAddress;

public abstract class PeerMixin extends Peer {
    @JsonCreator
    public PeerMixin(@JsonProperty("role") final Role role,
                     @JsonProperty("inetAddress") final InetSocketAddress inetAddress,
                     @JsonProperty("sent") final long sent,
                     @JsonProperty("last") final long last,
                     @JsonProperty("average") final long average,
                     @JsonProperty("best") final long best,
                     @JsonProperty("worst") final long worst,
                     @JsonProperty("stDev") final double stDev) {
        super(role, inetAddress, sent, last, average, best, worst, stDev);
    }

    @JsonGetter
    public abstract Role role();

    @JsonGetter
    public abstract InetSocketAddress inetAddress();

    @JsonGetter
    public abstract long sent();

    @JsonGetter
    public abstract long last();

    @JsonGetter
    public abstract double average();

    @JsonGetter
    public abstract long best();

    @JsonGetter
    public abstract long worst();

    @JsonGetter
    public abstract double stDev();
}
