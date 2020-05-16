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

import org.drasyl.peer.connection.message.action.MessageAction;
import org.drasyl.peer.connection.message.action.RequestClientsStocktakingMessageAction;

/**
 * A message representing a request for an aggregation of all children peers of the node.
 * <p>
 * Response of this request is a {@link ResponseMessage} object that has a {@link
 * ClientsStocktakingMessage} message.
 */
public class RequestClientsStocktakingMessage extends AbstractMessage<RequestClientsStocktakingMessage> implements RequestMessage<RequestClientsStocktakingMessage> {
    @Override
    public MessageAction<RequestClientsStocktakingMessage> getAction() {
        return new RequestClientsStocktakingMessageAction(this);
    }

    @Override
    public String toString() {
        return "RequestClientsStocktakingMessage{" +
                "id='" + id + '\'' +
                ", signature=" + signature +
                '}';
    }
}
