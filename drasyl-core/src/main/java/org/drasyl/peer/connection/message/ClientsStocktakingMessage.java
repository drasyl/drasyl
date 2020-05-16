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
package org.drasyl.peer.connection.message;

import org.drasyl.identity.Identity;
import org.drasyl.peer.connection.message.action.ClientsStocktakingMessageAction;
import org.drasyl.peer.connection.message.action.MessageAction;

import java.util.Collection;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * A message representing a response to a {@link RequestClientsStocktakingMessage} request.
 *
 * <p>
 * Note: Always wrapped in a {@link ResponseMessage} object.
 * </p>
 * <p>
 * Containing all connected clients of the node
 */
public class ClientsStocktakingMessage extends AbstractResponseMessage<RequestClientsStocktakingMessage, ClientsStocktakingMessage> {
    private final Collection<Identity> identities;

    ClientsStocktakingMessage() {
        super(null);
        identities = null;
    }

    /**
     * Creates a new ClientsStocktakingMessage.
     *
     * @param identities      list of all connected clients of the node
     * @param correspondingId
     */
    public ClientsStocktakingMessage(Collection<Identity> identities,
                                     String correspondingId) {
        super(correspondingId);
        this.identities = requireNonNull(identities);
    }

    /**
     * @return list of all connected clients of the node
     */
    public Collection<Identity> getIdentities() {
        return identities;
    }

    @Override
    public MessageAction<ClientsStocktakingMessage> getAction() {
        return new ClientsStocktakingMessageAction(this);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), identities);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        ClientsStocktakingMessage that = (ClientsStocktakingMessage) o;
        return Objects.equals(identities, that.identities);
    }

    @Override
    public String toString() {
        return "ClientsStocktakingMessage{" +
                "identities=" + identities +
                ", correspondingId='" + correspondingId + '\'' +
                ", id='" + id + '\'' +
                ", signature=" + signature +
                '}';
    }
}
