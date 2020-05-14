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
import org.drasyl.core.common.message.Message;
import org.drasyl.core.common.message.RequestMessage;
import org.drasyl.core.common.message.ResponseMessage;
import org.drasyl.core.node.ConnectionsManager;
import org.drasyl.core.node.identity.Identity;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * A {@link PeerConnection} object represents a connection to another peer, e.g. local or remote.
 * For this purpose, this object provides a standardized interface so that the actual connection
 * type is abstracted and the same operations are always available.
 */
public abstract class PeerConnection {
    protected Supplier<Identity> identitySupplier;

    public PeerConnection(Supplier<Identity> identitySupplier,
                          ConnectionsManager connectionsManager) {
        this.identitySupplier = identitySupplier;
        connectionsManager.addConnection(this, this::close);
    }

    /**
     * Sends a message to the peer without waiting for any response.
     *
     * @param message message that should be sent
     */
    public abstract void send(Message<?> message);

    /**
     * Sends a message to the peer and returns a {@link Single} object for potential responses to
     * this message.
     *
     * @param message       message that should be sent
     * @param responseClass the class of the response object, to avoid * ClassCastExceptions
     * @param <T>           the type of the response
     * @return a {@link Single} object that can be fulfilled with a {@link Message response} to the
     * * message
     */
    public abstract <T extends ResponseMessage<? extends RequestMessage<?>, ? extends Message<?>>> Single<T> send(
            RequestMessage<?> message,
            Class<T> responseClass);

    /**
     * Sets the result of a {@link Single} object from a {@link #send(RequestMessage, Class)} call.
     *
     * @param response the response
     */
    public abstract void setResponse(ResponseMessage<? extends RequestMessage<?>, ? extends Message<?>> response);

    /**
     * Returns the User-Agent string.
     */
    public abstract String getUserAgent();

    /**
     * Returns the endpoint of this connection.
     */
    public abstract URI getEndpoint();

    /**
     * Returns the identity of the peer.
     */
    public Identity getIdentity() {
        return identitySupplier.get();
    }

    /**
     * Causes the {@link PeerConnection} to close. All pending messages are still processed, but no
     * new messages can be sent after this method has been called.
     */
    protected abstract void close();

    /**
     * This {@link CompletableFuture} becomes complete as soon as this connection has been closed
     * successfully.
     */
    public abstract CompletableFuture<Boolean> isClosed();
}
