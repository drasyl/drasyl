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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Uses shared memory to discover other drasyl nodes running on same JVM.
 */
public class IntraVmDiscovery implements AutoCloseable {
    private static final ReadWriteLock lock = new ReentrantReadWriteLock(true);
    private static final Map<CompressedPublicKey, IntraVmDiscovery> discoveries = new HashMap<>();
    private static final MessageSink MESSAGE_SINK = message -> {
        CompressedPublicKey recipient = message.getRecipient();
        IntraVmDiscovery discoveree = discoveries.get(recipient);

        if (discoveree == null) {
            throw new NoPathToIdentityException(recipient);
        }

        discoveree.path.send(message);
    };
    private final Path path;
    private final Supplier<CompressedPublicKey> identitySupplier;
    private final PeersManager peersManager;
    private final PeerInformation peerInformation;
    private final Messenger messenger;
    private final AtomicBoolean opened;

    @SuppressWarnings({ "java:S1905" })
    public IntraVmDiscovery(Supplier<CompressedPublicKey> identitySupplier,
                            Messenger messenger,
                            PeersManager peersManager,
                            Consumer<Event> eventConsumer) {
        this(
                identitySupplier,
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

    public IntraVmDiscovery(Supplier<CompressedPublicKey> identitySupplier,
                            Messenger messenger,
                            PeersManager peersManager,
                            Path path) {
        this(identitySupplier, messenger, peersManager, path, PeerInformation.of(), new AtomicBoolean(false));
    }

    IntraVmDiscovery(Supplier<CompressedPublicKey> identitySupplier,
                     Messenger messenger,
                     PeersManager peersManager,
                     Path path,
                     PeerInformation peerInformation,
                     AtomicBoolean opened) {
        this.identitySupplier = identitySupplier;
        this.peersManager = peersManager;
        this.opened = opened;
        this.peerInformation = peerInformation;
        this.path = path;
        this.messenger = messenger;
    }

    public void open() {
        if (opened.compareAndSet(false, true)) {
            try {
                lock.writeLock().lock();

                // add message sink
                messenger.setIntraVmSink(MESSAGE_SINK);

                // store peer information
                discoveries.values().forEach(d -> {
                    d.peersManager.setPeerInformationAndAddPath(identitySupplier.get(), peerInformation, path);
                    peersManager.setPeerInformationAndAddPath(d.identitySupplier.get(), d.peerInformation, d.path);
                });
                discoveries.put(identitySupplier.get(), this);
            }
            finally {
                lock.writeLock().unlock();
            }
        }
    }

    @Override
    public void close() {
        if (opened.compareAndSet(true, false)) {
            try {
                lock.writeLock().lock();

                // remove message sink
                messenger.unsetIntraVmSink();

                // remove peer information
                discoveries.remove(identitySupplier.get());
                discoveries.values().forEach(d -> {
                    d.peersManager.removePath(identitySupplier.get(), path);
                    peersManager.removePath(d.identitySupplier.get(), path);
                });
            }
            finally {
                lock.writeLock().unlock();
            }
        }
    }
}
