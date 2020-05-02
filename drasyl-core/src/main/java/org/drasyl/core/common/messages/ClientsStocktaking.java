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
package org.drasyl.core.common.messages;

import org.drasyl.core.node.identity.Identity;

import java.util.Collection;
import java.util.Objects;

/**
 * A message representing a response to a {@link RequestClientsStocktaking} request.
 *
 * <p>
 * Note: Always wrapped in a {@link Response} object.
 * </p>
 * <p>
 * Containing all connected clients of the node
 */
public class ClientsStocktaking extends AbstractMessage {
    private final Collection<Identity> identities;

    ClientsStocktaking() {
        identities = null;
    }

    /**
     * Creates a new ClientsStocktakingMessage.
     *
     * @param identities list of all connected clients of the node
     */
    public ClientsStocktaking(Collection<Identity> identities) {
        this.identities = Objects.requireNonNull(identities);
    }

    /**
     * @return list of all connected clients of the node
     */
    public Collection<Identity> getIdentities() {
        return identities;
    }

    @Override
    public String toString() {
        return "ClientsStocktaking [identities=" + identities + ", messageID=" + getMessageID() + "]";
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
        return Objects.equals(getIdentities(), that.getIdentities());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), getIdentities());
    }
}
