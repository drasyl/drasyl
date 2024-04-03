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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.netty.channel.ChannelPipeline;
import io.netty.util.internal.StringUtil;
import org.drasyl.cli.sdo.handler.policy.ProactiveLatencyMeasurementsPolicyHandler;
import org.drasyl.handler.remote.ApplicationMessageToPayloadCodec;
import org.drasyl.identity.DrasylAddress;

import java.util.Objects;
import java.util.Set;

import static java.util.Objects.requireNonNull;

public class ProactiveLatencyMeasurementsPolicy extends Policy {
    private final Set<DrasylAddress> peers;

    @JsonCreator
    public ProactiveLatencyMeasurementsPolicy(@JsonProperty("peers") final Set<DrasylAddress> peers,
                                              @JsonProperty("currentState") final PolicyState currentState,
                                              @JsonProperty("desiredState") final PolicyState desiredState) {
        super(currentState, desiredState);
        this.peers = requireNonNull(peers);
    }

    public ProactiveLatencyMeasurementsPolicy(final Set<DrasylAddress> peers) {
        super();
        this.peers = requireNonNull(peers);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ProactiveLatencyMeasurementsPolicy that = (ProactiveLatencyMeasurementsPolicy) o;
        return Objects.equals(peers, that.peers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(peers);
    }

    @Override
    public String toString() {
        return "ProactiveLatencyMeasurementsPolicy{" +
                "peers=" + peers +
                ", currentState=" + currentState +
                ", desiredState=" + desiredState +
                '}';
    }

    @JsonGetter("peers")
    public Set<DrasylAddress> peers() {
        return peers;
    }

    public void addPolicy(final ChannelPipeline pipeline) {
        final String handlerName = StringUtil.simpleClassName(this);

        pipeline.addAfter(pipeline.context(ApplicationMessageToPayloadCodec.class).name(), handlerName, new ProactiveLatencyMeasurementsPolicyHandler(this));
    }

    @Override
    public void removePolicy(final ChannelPipeline pipeline) {
        final String handlerName = StringUtil.simpleClassName(this);
        pipeline.remove(handlerName);
    }
}
