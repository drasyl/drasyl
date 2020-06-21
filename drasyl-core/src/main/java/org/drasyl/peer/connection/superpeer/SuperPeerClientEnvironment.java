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
package org.drasyl.peer.connection.superpeer;

import io.reactivex.rxjava3.subjects.Subject;
import org.drasyl.DrasylNodeConfig;
import org.drasyl.event.Event;
import org.drasyl.identity.Identity;
import org.drasyl.messenger.Messenger;
import org.drasyl.peer.PeersManager;

import java.net.URI;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class SuperPeerClientEnvironment {
    private final DrasylNodeConfig config;
    private final Supplier<Identity> identitySupplier;
    private final URI endpoint;
    private final Messenger messenger;
    private final PeersManager peersManager;
    private final Subject<Boolean> connected;
    private final Consumer<Event> eventConsumer;

    public SuperPeerClientEnvironment(DrasylNodeConfig config,
                                      Supplier<Identity> identitySupplier,
                                      URI endpoint,
                                      Messenger messenger,
                                      PeersManager peersManager,
                                      Subject<Boolean> connected,
                                      Consumer<Event> eventConsumer) {
        this.config = config;
        this.identitySupplier = identitySupplier;
        this.endpoint = endpoint;
        this.messenger = messenger;
        this.peersManager = peersManager;
        this.connected = connected;
        this.eventConsumer = eventConsumer;
    }

    public DrasylNodeConfig getConfig() {
        return config;
    }

    public URI getEndpoint() {
        return endpoint;
    }

    public Identity getIdentity() {
        return identitySupplier.get();
    }

    public Messenger getMessenger() {
        return messenger;
    }

    public PeersManager getPeersManager() {
        return peersManager;
    }

    public Subject<Boolean> getConnected() {
        return connected;
    }

    public Consumer<Event> getEventConsumer() {
        return eventConsumer;
    }
}
