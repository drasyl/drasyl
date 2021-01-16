/*
 * Copyright (c) 2021.
 *
 * This file is part of drasyl.
 *
 *  drasyl is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  drasyl is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.drasyl.behaviour;

import org.drasyl.DrasylConfig;
import org.drasyl.DrasylException;
import org.drasyl.DrasylNode;
import org.drasyl.event.Event;
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.Pipeline;
import org.drasyl.plugin.PluginManager;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Objects.requireNonNull;

/**
 * Describes a {@link DrasylNode} as a finite state machine.
 */
@SuppressWarnings({ "java:S107", "java:S1192" })
public abstract class BehavioralDrasylNode extends DrasylNode {
    private static final Logger LOG = LoggerFactory.getLogger(BehavioralDrasylNode.class);
    Behavior behavior;

    @SuppressWarnings("unused")
    protected BehavioralDrasylNode() throws DrasylException {
        behavior = requireNonNull(created(), "initial behavior must not be null");
        if (behavior instanceof DeferredBehavior) {
            behavior = ((DeferredBehavior) behavior).apply(this);
        }
    }

    @SuppressWarnings("unused")
    protected BehavioralDrasylNode(final DrasylConfig config) throws DrasylException {
        super(config);
        behavior = requireNonNull(created(), "initial behavior must not be null");
        if (behavior instanceof DeferredBehavior) {
            behavior = ((DeferredBehavior) behavior).apply(this);
        }
    }

    @SuppressWarnings("unused")
    protected BehavioralDrasylNode(final DrasylConfig config,
                                   final Identity identity,
                                   final PeersManager peersManager,
                                   final AtomicBoolean acceptNewConnections,
                                   final Pipeline pipeline,
                                   final PluginManager pluginManager,
                                   final AtomicBoolean started,
                                   final CompletableFuture<Void> startSequence,
                                   final CompletableFuture<Void> shutdownSequence,
                                   final Behavior behavior) {
        super(config, identity, peersManager, acceptNewConnections, pipeline, pluginManager, started, startSequence, shutdownSequence);
        if (behavior instanceof DeferredBehavior) {
            this.behavior = ((DeferredBehavior) behavior).apply(this);
        }
        else {
            this.behavior = requireNonNull(behavior, "initial behavior must not be null");
        }
    }

    @Override
    public final void onEvent(final Event event) {
        Behavior result = behavior.receive(event);

        if (result instanceof DeferredBehavior) {
            result = ((DeferredBehavior) result).apply(this);
        }

        if (result == Behaviors.shutdown()) {
            shutdown();
        }

        if (result == Behaviors.unhandled()) {
            LOG.debug("Unhandled event: {}", event);
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
}
