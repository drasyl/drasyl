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
package org.drasyl.cli.sdon.message;

import com.fasterxml.jackson.annotation.*;
import org.drasyl.cli.sdon.config.Policy;
import org.drasyl.handler.peers.PeersList;

import java.util.Map;
import java.util.Set;

import static java.util.Objects.requireNonNull;

public class NodeHello implements SdonMessage {
    private final Set<Policy> policies;
    private final PeersList peersList;
    private final Map<String, Object> store;

    @JsonCreator
    public NodeHello(@JsonProperty("policies") final Set<Policy> policies,
                     @JsonProperty("peersList") final PeersList peersList,
                     @JsonProperty("store") final Map<String, Object> store) {
        this.policies = requireNonNull(policies);
        this.peersList = requireNonNull(peersList);
        this.store = requireNonNull(store);
    }

    @JsonGetter("policies")
    public Set<Policy> policies() {
        return policies;
    }

    @JsonGetter("peersList")
    public PeersList peersList() {
        return peersList;
    }

    @JsonGetter("store")
    public Map<String, Object> store() {
        return store;
    }

    @Override
    public String toString() {
        return "NodeHello{" +
                "policies='" + policies + '\'' +
                ", peersList='" + peersList + '\'' +
                ", store='" + store + '\'' +
                '}';
    }
}
