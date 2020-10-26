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
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.drasyl.peer.connection.message.Message;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.Pipeline;
import org.drasyl.pipeline.SimpleOutboundHandler;
import org.drasyl.util.FutureUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.drasyl.peer.connection.pipeline.LoopbackMessageSinkHandler.LOOPBACK_MESSAGE_SINK_HANDLER;

/**
 * Uses shared memory to discover other drasyl nodes running on same JVM.
 * <p>
 * Inspired by: https://github.com/actoron/jadex/blob/10e464b230d7695dfd9bf2b36f736f93d69ee314/platform/base/src/main/java/jadex/platform/service/awareness/IntraVMAwarenessAgent.java
 */
public class IntraVmDiscovery implements DrasylNodeComponent {
    public static final String INTRA_VM_SINK_HANDLER = "intraVmSink";
    private static final Logger LOG = LoggerFactory.getLogger(IntraVmDiscovery.class);
    static final Map<CompressedPublicKey, IntraVmDiscovery> discoveries = new HashMap<>();
    private static final ReadWriteLock lock = new ReentrantReadWriteLock(true);
    private final Path path;
    private final CompressedPublicKey publicKey;
    private final PeersManager peersManager;
    private final PeerInformation peerInformation;
    private final AtomicBoolean opened;

    @SuppressWarnings({ "java:S1905" })
    public IntraVmDiscovery(final CompressedPublicKey publicKey,
                            final PeersManager peersManager,
                            final Pipeline pipeline) {
        this(
                publicKey,
                peersManager,
                message -> {
                    if (!(message instanceof ApplicationMessage)) {
                        throw new IllegalArgumentException("IntraVmDiscovery.messageSink can only handle messages of type " + ApplicationMessage.class.getSimpleName());
                    }

                    final ApplicationMessage applicationMessage = (ApplicationMessage) message;
                    pipeline.processInbound(applicationMessage);

                    return completedFuture(null);
                }
        );

        pipeline.addBefore(LOOPBACK_MESSAGE_SINK_HANDLER, INTRA_VM_SINK_HANDLER, new SimpleOutboundHandler<Message, CompressedPublicKey>() {
            @Override
            protected void matchedWrite(final HandlerContext ctx,
                                        final CompressedPublicKey recipient,
                                        final Message msg,
                                        final CompletableFuture<Void> future) {
                if (opened.get()) {
                    final IntraVmDiscovery discoveree = discoveries.get(recipient);

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
        });
    }

    IntraVmDiscovery(final CompressedPublicKey publicKey,
                     final PeersManager peersManager,
                     final Path path) {
        this(publicKey, peersManager, path, PeerInformation.of(), new AtomicBoolean(false));
    }

    IntraVmDiscovery(final CompressedPublicKey publicKey,
                     final PeersManager peersManager,
                     final Path path,
                     final PeerInformation peerInformation,
                     final AtomicBoolean opened) {
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
                discoveries.values().forEach(d -> {
                    d.peersManager.setPeerInformationAndAddPath(publicKey, peerInformation, path);
                    peersManager.setPeerInformationAndAddPath(d.publicKey, d.peerInformation, d.path);
                });
                discoveries.put(publicKey, this);
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
                discoveries.remove(publicKey);
                discoveries.values().forEach(d -> {
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
}