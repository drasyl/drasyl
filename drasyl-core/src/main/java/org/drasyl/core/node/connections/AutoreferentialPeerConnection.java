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
package org.drasyl.core.node.connections;

import io.reactivex.rxjava3.core.Single;
import org.drasyl.core.common.message.*;
import org.drasyl.core.common.models.Pair;
import org.drasyl.core.models.Code;
import org.drasyl.core.models.Event;
import org.drasyl.core.node.ConnectionsManager;
import org.drasyl.core.node.DrasylNode;
import org.drasyl.core.node.identity.Identity;

import java.net.URI;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * The {@link AutoreferentialPeerConnection} object models an autoreferentially connection of this
 * node.
 */
public class AutoreferentialPeerConnection extends PeerConnection {
    private final Consumer<Event> onEvent;
    private final URI endpoint;
    protected CompletableFuture<Boolean> closedCompletable;
    protected AtomicBoolean isClosed;

    AutoreferentialPeerConnection(Consumer<Event> onEvent,
                                  Identity identity, URI endpoint,
                                  CompletableFuture<Boolean> closedCompletable,
                                  AtomicBoolean isClosed, ConnectionsManager connectionsManager) {
        super(identity, connectionsManager);
        this.onEvent = onEvent;
        this.endpoint = endpoint;
        this.closedCompletable = closedCompletable;
        this.isClosed = isClosed;
    }

    /**
     * Creates a new autoreferentially connection of this node.
     *
     * @param onEvent            reference to {@link DrasylNode#onEvent(Event)}
     * @param identity    reference to {@link Identity}
     * @param uri                the node endpoint
     * @param connectionsManager reference to the {@link ConnectionsManager}
     */
    public AutoreferentialPeerConnection(Consumer<Event> onEvent,
                                         Identity identity,
                                         URI uri,
                                         ConnectionsManager connectionsManager) {
        this(onEvent, identity, uri, new CompletableFuture<>(), new AtomicBoolean(false), connectionsManager);
    }

    @Override
    public void send(Message<?> message) {
        if (isClosed.get()) {
            return;
        }

        if (!(message instanceof ApplicationMessage)) {
            throw new IllegalArgumentException("AutoreferentialPeerConnection can only handle ApplicationMessage's.");
        }

        ApplicationMessage applicationMessage = (ApplicationMessage) message;
        onEvent.accept(new Event(Code.MESSAGE, Pair.of(applicationMessage.getSender(), applicationMessage.getPayload())));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends ResponseMessage<? extends RequestMessage<?>, ? extends Message<?>>> Single<T> send(
            RequestMessage<?> message, Class<T> responseClass) {
        send(message);

        if (StatusMessage.class.isAssignableFrom(responseClass)) {
            return (Single<T>) Single.just(new StatusMessage(StatusMessage.Code.STATUS_OK, message.getId()));
        }

        return Single.error(new IllegalArgumentException("Only StatusMessage is allowed as return type"));
    }

    @Override
    public void setResponse(ResponseMessage<? extends RequestMessage<?>, ? extends Message<?>> response) {
        // Do nothing
    }

    @Override
    public String getUserAgent() {
        return AbstractMessageWithUserAgent.userAgentGenerator.get();
    }

    @Override
    public URI getEndpoint() {
        return endpoint;
    }

    @Override
    protected void close() {
        if (isClosed.compareAndSet(false, true)) {
            closedCompletable.complete(true);
        }
    }

    @Override
    public CompletableFuture<Boolean> isClosed() {
        return closedCompletable;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AutoreferentialPeerConnection that = (AutoreferentialPeerConnection) o;
        return Objects.equals(getIdentity(), that.getIdentity());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getIdentity());
    }
}
