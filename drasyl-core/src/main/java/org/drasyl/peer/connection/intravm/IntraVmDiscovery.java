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
import org.drasyl.event.Event;
import org.drasyl.event.MessageEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.messenger.MessageSink;
import org.drasyl.messenger.Messenger;
import org.drasyl.messenger.NoPathToIdentityException;
import org.drasyl.peer.Path;
import org.drasyl.peer.PeerInformation;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.drasyl.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

/**
 * Uses shared memory to discover other drasyl nodes running on same JVM.
 */
public class IntraVmDiscovery implements DrasylNodeComponent {
    private static final Logger LOG = LoggerFactory.getLogger(IntraVmDiscovery.class);
    static final Map<CompressedPublicKey, IntraVmDiscovery> discoveries = new HashMap<>();
    static final MessageSink MESSAGE_SINK = message -> {
        CompressedPublicKey recipient = message.getRecipient();
        IntraVmDiscovery discoveree = discoveries.get(recipient);

        if (discoveree == null) {
            throw new NoPathToIdentityException(recipient);
        }

        discoveree.path.send(message);
    };
    private static final ReadWriteLock lock = new ReentrantReadWriteLock(true);
    private final Path path;
    private final CompressedPublicKey publicKey;
    private final PeersManager peersManager;
    private final PeerInformation peerInformation;
    private final Messenger messenger;
    private final AtomicBoolean opened;

    @SuppressWarnings({ "java:S1905" })
    public IntraVmDiscovery(CompressedPublicKey publicKey,
                            Messenger messenger,
                            PeersManager peersManager,
                            Consumer<Event> eventConsumer) {
        this(
                publicKey,
                messenger,
                peersManager,
                (Path) message -> {
                    if (!(message instanceof ApplicationMessage)) {
                        throw new IllegalArgumentException("IntraVmDiscovery.messageSink can only handle messages of type " + ApplicationMessage.class.getSimpleName());
                    }

                    ApplicationMessage applicationMessage = (ApplicationMessage) message;
                    eventConsumer.accept(new MessageEvent(Pair.of(applicationMessage.getSender(), applicationMessage.getPayload())));
                }
        );
    }

    public IntraVmDiscovery(CompressedPublicKey publicKey,
                            Messenger messenger,
                            PeersManager peersManager,
                            Path path) {
        this(publicKey, messenger, peersManager, path, PeerInformation.of(), new AtomicBoolean(false));
    }

    IntraVmDiscovery(CompressedPublicKey publicKey,
                     Messenger messenger,
                     PeersManager peersManager,
                     Path path,
                     PeerInformation peerInformation,
                     AtomicBoolean opened) {
        this.publicKey = publicKey;
        this.peersManager = peersManager;
        this.opened = opened;
        this.peerInformation = peerInformation;
        this.path = path;
        this.messenger = messenger;
    }

    @Override
    public void open() {
        try {
            lock.writeLock().lock();
            LOG.debug("Start Intra VM Discovery...");

            if (opened.compareAndSet(false, true)) {
                // add message sink
                messenger.setIntraVmSink(MESSAGE_SINK);

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
            LOG.info("Stop Intra VM Discovery...");

            if (opened.compareAndSet(true, false)) {
                // remove message sink
                messenger.unsetIntraVmSink();

                // remove peer information
                discoveries.remove(publicKey);
                discoveries.values().forEach(d -> {
                    d.peersManager.removePath(publicKey, path);
                    peersManager.removePath(d.publicKey, path);
                });
            }
            LOG.info("Intra VM Discovery stopped.");
        }
        finally {
            lock.writeLock().unlock();
        }
    }
}
