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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.drasyl.peer.connection.server.Server;

/**
 * Describes messages that are sent by the {@link Server} or a client.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
@JsonSubTypes({
        @JsonSubTypes.Type(value = ApplicationMessage.class),
        @JsonSubTypes.Type(value = ConnectionExceptionMessage.class),
        @JsonSubTypes.Type(value = ChunkedMessage.class),
        @JsonSubTypes.Type(value = ExceptionMessage.class),
        @JsonSubTypes.Type(value = IdentityMessage.class),
        @JsonSubTypes.Type(value = JoinMessage.class),
        @JsonSubTypes.Type(value = PingMessage.class),
        @JsonSubTypes.Type(value = PongMessage.class),
        @JsonSubTypes.Type(value = QuitMessage.class),
        @JsonSubTypes.Type(value = SignedMessage.class),
        @JsonSubTypes.Type(value = StatusMessage.class),
        @JsonSubTypes.Type(value = WelcomeMessage.class),
        @JsonSubTypes.Type(value = WhoisMessage.class),
})
@JsonIgnoreProperties(ignoreUnknown = true)
public interface Message {
    /**
     * @return the unique id of this message. Each message generates a random id when it is created.
     */
    MessageId getId();
}