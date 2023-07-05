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
import org.drasyl.cli.sdo.handler.policy.DefaultRoutePolicyHandler;
import org.drasyl.handler.remote.ApplicationMessageToPayloadCodec;
import org.drasyl.identity.DrasylAddress;

import java.util.Objects;

import static java.util.Objects.requireNonNull;

public class DefaultRoutePolicy extends Policy {
    private final DrasylAddress defaultRoute;

    @JsonCreator
    public DefaultRoutePolicy(@JsonProperty("defaultRoute") final DrasylAddress defaultRoute,
                              @JsonProperty("currentState") final PolicyState currentState,
                              @JsonProperty("desiredState") final PolicyState desiredState) {
        super(currentState, desiredState);
        this.defaultRoute = requireNonNull(defaultRoute);
    }

    public DefaultRoutePolicy(final DrasylAddress defaultRoute) {
        super();
        this.defaultRoute = requireNonNull(defaultRoute);
    }

    @JsonGetter("defaultRoute")
    public DrasylAddress defaultRoute() {
        return defaultRoute;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DefaultRoutePolicy that = (DefaultRoutePolicy) o;
        return Objects.equals(defaultRoute, that.defaultRoute);
    }

    @Override
    public int hashCode() {
        return Objects.hash(defaultRoute);
    }

    @Override
    public String toString() {
        return "DefaultRoutePolicy{" +
                "defaultRoute=" + defaultRoute +
                ", currentState=" + currentState +
                ", desiredState=" + desiredState +
                '}';
    }

    public void addPolicy(final ChannelPipeline pipeline) {
        final String handlerName = StringUtil.simpleClassName(this);

        pipeline.addAfter(pipeline.context(ApplicationMessageToPayloadCodec.class).name(), handlerName, new DefaultRoutePolicyHandler(this));
    }

    @Override
    public void removePolicy(final ChannelPipeline pipeline) {
        final String handlerName = StringUtil.simpleClassName(this);
        pipeline.remove(handlerName);
    }
}
