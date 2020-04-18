/*
 * Copyright (c) 2020
 *
 * This file is part of Relayserver.
 *
 * Relayserver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Relayserver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Relayserver.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.drasyl.core.common.messages;

import org.drasyl.core.common.models.SessionUID;

import java.util.Collection;
import java.util.Objects;

/**
 * A message representing a response to a
 * {@link RequestClientsStocktaking} request.
 *
 * <p>
 * Note: Always wrapped in a {@link Response} object.
 * </p>
 * <p>
 * Containing all connected clients of the network
 */
public class ClientsStocktaking extends AbstractMessage {
    private final Collection<SessionUID> clientUIDs;

    ClientsStocktaking() {
        clientUIDs = null;
    }

    /**
     * Creates a new ClientsStocktakingMessage.
     *
     * @param clientUIDs list of all connected clients of the network
     */
    public ClientsStocktaking(Collection<SessionUID> clientUIDs) {
        this.clientUIDs = Objects.requireNonNull(clientUIDs);
    }

    /**
     * @return list of all connected clients of the network
     */
    public Collection<SessionUID> getClientUIDs() {
        return clientUIDs;
    }

    @Override
    public String toString() {
        return "ClientsStocktaking [clientUIDs=" + clientUIDs + ", messageID=" + getMessageID() + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ClientsStocktaking)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        ClientsStocktaking that = (ClientsStocktaking) o;
        return Objects.equals(getClientUIDs(), that.getClientUIDs());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), getClientUIDs());
    }
}
