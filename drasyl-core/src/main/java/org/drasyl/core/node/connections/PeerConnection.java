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
import org.drasyl.core.common.messages.IMessage;
import org.drasyl.core.common.messages.Response;
import org.drasyl.core.node.identity.Identity;

import java.net.URI;
import java.util.concurrent.CompletableFuture;

/**
 * A {@link PeerConnection} object represents a connection to another peer, e.g. local or remote.
 * For this purpose, this object provides a standardized interface so that the actual connection
 * type is abstracted and the same operations are always available.
 */
public interface PeerConnection extends AutoCloseable {
    /**
     * Sends a message to the peer without waiting for any response.
     *
     * @param message message that should be sent
     */
    void send(IMessage message);

    /**
     * Sends a message to the peer and returns a {@link Single} object for potential responses to
     * this message.
     *
     * @param message       message that should be sent
     * @param responseClass the class of the response object, to avoid * ClassCastExceptions
     * @param <T>           the type of the response
     * @return a {@link Single} object that can be fulfilled with a {@link IMessage response} to the
     * * message
     */
    <T extends IMessage> Single<T> send(IMessage message, Class<T> responseClass);

    /**
     * Sets the result of a {@link Single} object from a {@link #send(IMessage, Class)} call.
     *
     * @param response the response
     */
    void setResponse(Response<? extends IMessage> response);

    /**
     * Returns the User-Agent string.
     */
    String getUserAgent();

    /**
     * Returns the endpoint of this connection.
     */
    URI getEndpoint();

    /**
     * Returns the identity of the peer.
     */
    Identity getIdentity();

    /**
     * Causes the {@link PeerConnection} to close. All pending messages are still processed, but no
     * new messages can be sent after this method has been called.
     */
    @Override
    void close();

    /**
     * This {@link CompletableFuture} becomes complete as soon as this connection has been closed
     * successfully.
     */
    CompletableFuture<Boolean> isClosed();

    /**
     * Returns a unique identifier of this connection, so that this return value can be used to sort
     * in lists.
     */
    String getConnectionId();
}
