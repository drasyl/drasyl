/*
 * Copyright (c) 2020.
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
package org.drasyl.peer.connection.intravm;

import org.drasyl.DrasylNodeComponent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.peer.Path;
import org.drasyl.peer.PeerInformation;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.message.Message;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.Pipeline;
import org.drasyl.pipeline.skeleton.SimpleInboundHandler;
import org.drasyl.pipeline.skeleton.SimpleOutboundHandler;
import org.drasyl.pipeline.address.Address;
import org.drasyl.util.FutureUtil;
import org.drasyl.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.drasyl.pipeline.DirectConnectionInboundMessageSinkHandler.DIRECT_CONNECTION_INBOUND_MESSAGE_SINK_HANDLER;
import static org.drasyl.pipeline.DirectConnectionOutboundMessageSinkHandler.DIRECT_CONNECTION_OUTBOUND_MESSAGE_SINK_HANDLER;

/**
 * Uses shared memory to discover other drasyl nodes running on same JVM.
 * <p>
 * Inspired by: https://github.com/actoron/jadex/blob/10e464b230d7695dfd9bf2b36f736f93d69ee314/platform/base/src/main/java/jadex/platform/service/awareness/IntraVMAwarenessAgent.java
 */
public class IntraVmDiscovery implements DrasylNodeComponent {
    public static final String INTRA_VM_INBOUND_MESSAGE_SINK_HANDLER = "INTRA_VM_INBOUND_MESSAGE_SINK_HANDLER";
    public static final String INTRA_VM_OUTBOUND_MESSAGE_SINK_HANDLER = "INTRA_VM_OUTBOUND_MESSAGE_SINK_HANDLER";
    private static final Logger LOG = LoggerFactory.getLogger(IntraVmDiscovery.class);
    static final Map<Pair<Integer, CompressedPublicKey>, IntraVmDiscovery> discoveries = new HashMap<>();
    private static final ReadWriteLock lock = new ReentrantReadWriteLock(true);
    private final Path path;
    private final CompressedPublicKey publicKey;
    private final PeersManager peersManager;
    private final PeerInformation peerInformation;
    private final AtomicBoolean opened;
    private final int networkId;

    @SuppressWarnings({ "java:S1905" })
    public IntraVmDiscovery(final int networkId,
                            final CompressedPublicKey publicKey,
                            final PeersManager peersManager,
                            final Pipeline pipeline) {
        this(
                networkId,
                publicKey,
                peersManager,
                pipeline::processInbound
        );

        pipeline.addBefore(DIRECT_CONNECTION_INBOUND_MESSAGE_SINK_HANDLER, INTRA_VM_INBOUND_MESSAGE_SINK_HANDLER, new InboundMessageHandler(opened));
        pipeline.addBefore(DIRECT_CONNECTION_OUTBOUND_MESSAGE_SINK_HANDLER, INTRA_VM_OUTBOUND_MESSAGE_SINK_HANDLER, new OutboundMessageHandler(opened));
    }

    IntraVmDiscovery(final int networkId,
                     final CompressedPublicKey publicKey,
                     final PeersManager peersManager,
                     final Path path) {
        this(networkId, publicKey, peersManager, path, PeerInformation.of(), new AtomicBoolean(false));
    }

    IntraVmDiscovery(final int networkId,
                     final CompressedPublicKey publicKey,
                     final PeersManager peersManager,
                     final Path path,
                     final PeerInformation peerInformation,
                     final AtomicBoolean opened) {
        this.networkId = networkId;
        this.publicKey = publicKey;
        this.peersManager = peersManager;
        this.opened = opened;
        this.peerInformation = peerInformation;
        this.path = path;
    }

    @Override
    public void open() {
        try {
            lock.writeLock().lock();
            LOG.debug("Start Intra VM Discovery...");

            if (opened.compareAndSet(false, true)) {
                // store peer information
                discoveries.values().stream().filter(d -> networkId == d.networkId).forEach(d -> {
                    d.peersManager.setPeerInformationAndAddPath(publicKey, peerInformation, path);
                    peersManager.setPeerInformationAndAddPath(d.publicKey, d.peerInformation, d.path);
                });
                discoveries.put(Pair.of(networkId, publicKey), this);
            }
            LOG.debug("Intra VM Discovery started.");
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void close() {
        try {
            lock.writeLock().lock();
            LOG.debug("Stop Intra VM Discovery...");

            if (opened.compareAndSet(true, false)) {
                // remove peer information
                discoveries.remove(Pair.of(networkId, publicKey));
                discoveries.values().stream().filter(d -> networkId == d.networkId).forEach(d -> {
                    d.peersManager.removePath(publicKey, path);
                    peersManager.removePath(d.publicKey, path);
                });
            }
            LOG.debug("Intra VM Discovery stopped.");
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    static class InboundMessageHandler extends SimpleInboundHandler<Message, Address> {
        private final AtomicBoolean opened;

        public InboundMessageHandler(final AtomicBoolean opened) {
            this.opened = opened;
        }

        @Override
        protected void matchedRead(final HandlerContext ctx,
                                   final Address sender,
                                   final Message msg,
                                   final CompletableFuture<Void> future) {
            final CompressedPublicKey recipient = msg.getRecipient();
            if (opened.get() && !ctx.identity().getPublicKey().equals(recipient)) {
                final IntraVmDiscovery discoveree = discoveries.get(Pair.of(ctx.config().getNetworkId(), recipient));

                if (discoveree == null) {
                    ctx.fireRead(sender, msg, future);
                }
                else {
                    FutureUtil.completeOnAllOf(future, discoveree.path.send(msg));
                }
            }
            else {
                ctx.fireRead(sender, msg, future);
            }
        }
    }

    static class OutboundMessageHandler extends SimpleOutboundHandler<Message, CompressedPublicKey> {
        private final AtomicBoolean opened;

        public OutboundMessageHandler(final AtomicBoolean opened) {
            this.opened = opened;
        }

        @Override
        protected void matchedWrite(final HandlerContext ctx,
                                    final CompressedPublicKey recipient,
                                    final Message msg,
                                    final CompletableFuture<Void> future) {
            if (opened.get()) {
                final IntraVmDiscovery discoveree = discoveries.get(Pair.of(ctx.config().getNetworkId(), recipient));

                if (discoveree == null) {
                    ctx.write(recipient, msg, future);
                }
                else {
                    FutureUtil.completeOnAllOf(future, discoveree.path.send(msg));
                }
            }
            else {
                ctx.write(recipient, msg, future);
            }
        }
    }
}