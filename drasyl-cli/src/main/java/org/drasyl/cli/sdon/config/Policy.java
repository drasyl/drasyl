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
package org.drasyl.cli.sdon.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.netty.channel.ChannelPipeline;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import static java.util.Objects.requireNonNull;
import static org.drasyl.cli.sdon.config.Policy.PolicyState.ABSENT;
import static org.drasyl.cli.sdon.config.Policy.PolicyState.PRESENT;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
@JsonSubTypes({
        @Type(IpPolicy.class),
        @Type(LinkPolicy.class),
})
public abstract class Policy {
    private static final Logger LOG = LoggerFactory.getLogger(Policy.class);
    public PolicyState currentState;
    public PolicyState desiredState;

    protected Policy(final PolicyState currentState, final PolicyState desiredState) {
        this.currentState = currentState;
        this.desiredState = desiredState;
    }

    protected Policy() {
        this(ABSENT, PRESENT);
    }

    @JsonIgnore
    public void setCurrentState(final PolicyState state) {
        if (currentState != state) {
            LOG.error("Policy `{}` went to state `{}`.", this, state);
        }
        this.currentState = requireNonNull(state);
    }

    public abstract void addPolicy(final ChannelPipeline pipeline);

    public abstract void removePolicy(final ChannelPipeline pipeline);

    public PolicyState currentState() {
        return currentState;
    }

    public PolicyState desiredState() {
        return desiredState;
    }

    public enum PolicyState {
        ABSENT,
        READY,
        PRESENT,
    }
}
