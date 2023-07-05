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
package org.drasyl.cli.sdo.config;

import com.fasterxml.jackson.annotation.*;
import io.netty.channel.ChannelPipeline;
import io.netty.util.internal.StringUtil;
import org.drasyl.cli.sdo.handler.policy.LinkPolicyHandler;
import org.drasyl.handler.remote.ApplicationMessageToPayloadCodec;
import org.drasyl.identity.DrasylAddress;

import java.util.Objects;

import static java.util.Objects.requireNonNull;

public class LinkPolicy extends Policy {
    private final DrasylAddress peer;

    @JsonCreator
    public LinkPolicy(@JsonProperty("peer") final DrasylAddress peer,
                      @JsonProperty("currentState") final PolicyState currentState,
                      @JsonProperty("desiredState") final PolicyState desiredState) {
        super(currentState, desiredState);
        this.peer = requireNonNull(peer);
    }

    public LinkPolicy(final DrasylAddress peer) {
        super();
        this.peer = requireNonNull(peer);
    }

    @JsonGetter("peer")
    public DrasylAddress peer() {
        return peer;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        LinkPolicy that = (LinkPolicy) o;
        return Objects.equals(peer, that.peer);
    }

    @Override
    public int hashCode() {
        return Objects.hash(peer);
    }

    @Override
    public String toString() {
        return "LinkPolicy{" +
                "peer=" + peer +
                ", currentState=" + currentState +
                ", desiredState=" + desiredState +
                '}';
    }

    public void addPolicy(final ChannelPipeline pipeline) {
        final String handlerName = StringUtil.simpleClassName(this) + "-" + peer.toString();

        pipeline.addAfter(pipeline.context(ApplicationMessageToPayloadCodec.class).name(), handlerName, new LinkPolicyHandler(this));
    }

    @Override
    public void removePolicy(final ChannelPipeline pipeline) {
        final String handlerName = StringUtil.simpleClassName(this) + "-" + peer.toString();
        pipeline.remove(handlerName);
    }
}
