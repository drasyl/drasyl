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
package org.drasyl.core.common.message;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.drasyl.core.common.message.action.MessageAction;
import org.drasyl.crypto.Signable;

/**
 * Describes messages that are sent by the {@link org.drasyl.core.server.NodeServer} or a client.
 *
 * @param <T>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
@JsonSubTypes({
        @JsonSubTypes.Type(value = ApplicationMessage.class),
        @JsonSubTypes.Type(value = ClientsStocktakingMessage.class),
        @JsonSubTypes.Type(value = ConnectionExceptionMessage.class),
        @JsonSubTypes.Type(value = JoinMessage.class),
        @JsonSubTypes.Type(value = LeaveMessage.class),
        @JsonSubTypes.Type(value = MessageExceptionMessage.class),
        @JsonSubTypes.Type(value = PingMessage.class),
        @JsonSubTypes.Type(value = PongMessage.class),
        @JsonSubTypes.Type(value = RejectMessage.class),
        @JsonSubTypes.Type(value = RequestClientsStocktakingMessage.class),
        @JsonSubTypes.Type(value = ResponseMessage.class),
        @JsonSubTypes.Type(value = StatusMessage.class),
        @JsonSubTypes.Type(value = WelcomeMessage.class),
})
public interface Message<T extends Message> extends Signable {
    /**
     * Returns the unique id of this message. Each message generates a random id when it is
     * created.
     *
     * @return
     */
    String getId();

    /**
     * Returns a MesssageAction object that describes how a server or client should process received
     * messages.
     *
     * @return
     */
    @JsonIgnore
    MessageAction<T> getAction();
}
