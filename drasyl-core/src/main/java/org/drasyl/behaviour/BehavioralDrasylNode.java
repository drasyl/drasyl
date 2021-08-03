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
package org.drasyl.behaviour;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import org.drasyl.DrasylConfig;
import org.drasyl.DrasylException;
import org.drasyl.DrasylNode;
import org.drasyl.annotation.NonNull;
import org.drasyl.behaviour.Behavior.BehaviorBuilder;
import org.drasyl.channel.DrasylBootstrap;
import org.drasyl.event.Event;
import org.drasyl.plugin.PluginManager;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import static java.util.Objects.requireNonNull;

/**
 * Describes a {@link DrasylNode} as a finite state machine.
 * <p>
 * Note: Unlike the default {@link DrasylNode}, this node can only process one event at a time.
 * Please consider to run long-running operations asynchronously in a separate thread.
 */
@SuppressWarnings({ "java:S107", "java:S118", "java:S1192", "java:S1312" })
public abstract class BehavioralDrasylNode extends DrasylNode {
    private final Logger logger;
    Behavior behavior;

    @SuppressWarnings("unused")
    protected BehavioralDrasylNode() throws DrasylException {
        logger = LoggerFactory.getLogger(getClass());
        behavior = requireNonNull(created(), "initial behavior must not be null");
        if (behavior instanceof DeferredBehavior) {
            behavior = ((DeferredBehavior) behavior).apply(this);
        }
    }

    @SuppressWarnings("unused")
    protected BehavioralDrasylNode(final DrasylConfig config) throws DrasylException {
        super(config);
        logger = LoggerFactory.getLogger(getClass());
        behavior = requireNonNull(created(), "initial behavior must not be null");
        if (behavior instanceof DeferredBehavior) {
            behavior = ((DeferredBehavior) behavior).apply(this);
        }
    }

    @SuppressWarnings("unused")
    protected BehavioralDrasylNode(final DrasylBootstrap bootstrap,
                                   final PluginManager pluginManager,
                                   final ChannelFuture channelFuture,
                                   final Channel channel,
                                   final Behavior behavior) {
        super(bootstrap, pluginManager, channelFuture);
        logger = LoggerFactory.getLogger(getClass());
        if (behavior instanceof DeferredBehavior) {
            this.behavior = ((DeferredBehavior) behavior).apply(this);
        }
        else {
            this.behavior = requireNonNull(behavior, "initial behavior must not be null");
        }
    }

    @SuppressWarnings("unused")
    protected BehavioralDrasylNode(final DrasylBootstrap bootstrap,
                                   final PluginManager pluginManager,
                                   final ChannelFuture channelFuture) {
        super(bootstrap, pluginManager, channelFuture);
        logger = LoggerFactory.getLogger(getClass());
        behavior = requireNonNull(created(), "initial behavior must not be null");
        if (behavior instanceof DeferredBehavior) {
            behavior = ((DeferredBehavior) behavior).apply(this);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public final synchronized void onEvent(final @NonNull Event event) {
        Behavior result = behavior.receive(event);

        if (result instanceof DeferredBehavior) {
            result = ((DeferredBehavior) result).apply(this);
        }

        final Behavior finalResult = result;
        logger.debug("[{}] old behavior={}; event={}; new behavior={}", identity()::getIdentityPublicKey, () -> behavior, () -> event, () -> finalResult);

        if (result == Behaviors.shutdown()) {
            shutdown();
        }

        if (result == Behaviors.unhandled()) {
            logger.debug("Unhandled event: {}", event);
        }
        else if (result != Behaviors.same()) {
            behavior = result;
        }
    }

    /**
     * Returns the initial {@code Behavior} of the node.
     *
     * @return the initial {@code Behavior}
     */
    protected abstract Behavior created();

    /**
     * Creates a new {@link BehaviorBuilder} to build a new {@link Behavior} for inbound
     * message/event handling.
     */
    protected BehaviorBuilder newBehaviorBuilder() {
        return Behaviors.receive();
    }
}
