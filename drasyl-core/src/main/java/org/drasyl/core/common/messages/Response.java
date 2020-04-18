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

import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * A feedback is a data object that sets a message in reference to an other
 * message. This enables the recipient, for example, to work with futures.
 */
public class Response<T extends Message> extends AbstractMessage {
    private final T message;
    private final String msgID;

    protected Response() {
        message = null;
        msgID = null;
    }

    /**
     * Creates an immutable feedback object.
     *
     * @param message message
     * @param msgID   the id of the corresponding message
     */
    public Response(T message, String msgID) {
        this.message = requireNonNull(message);
        this.msgID = requireNonNull(msgID);
    }

    /**
     * @return the message
     */
    public T getMessage() {
        return message;
    }

    /**
     * @return the msgID
     */
    public String getMsgID() {
        return msgID;
    }

    @Override
    public int hashCode() {
        return Objects.hash(message, msgID);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Response) {
            Response res2 = (Response) obj;

            return Objects.equals(message, res2.message) && Objects.equals(msgID, res2.msgID);
        }

        return false;
    }

    @Override
    public String toString() {
        return "Response [message=" + getMessage().toString() + ", msgID=" + msgID + ", messageID=" + getMessageID()
                + "]";
    }
}
