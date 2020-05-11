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

import org.drasyl.core.common.message.action.MessageAction;
import org.drasyl.core.common.message.action.ResponseMessageAction;

import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * A feedback is a data object that sets a message in reference to an other message. This enables
 * the recipient, for example, to work with futures.
 */
public class ResponseMessage<T extends Message> extends AbstractMessage<ResponseMessage> {
    private final T message;
    private final String correspondingId;

    protected ResponseMessage() {
        message = null;
        correspondingId = null;
    }

    /**
     * Creates an immutable feedback object.
     *
     * @param message         message
     * @param correspondingId the id of the corresponding message
     */
    public ResponseMessage(T message, String correspondingId) {
        this.message = requireNonNull(message);
        this.correspondingId = requireNonNull(correspondingId);
    }

    /**
     * @return the msgID
     */
    public String getCorrespondingId() {
        return correspondingId;
    }

    /**
     * @return the message
     */
    public T getMessage() {
        return message;
    }

    @Override
    public MessageAction<ResponseMessage> getAction() {
        return new ResponseMessageAction(this);
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
        ResponseMessage<?> response = (ResponseMessage<?>) o;
        return Objects.equals(message, response.message) &&
                Objects.equals(correspondingId, response.correspondingId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), message, correspondingId);
    }

    @Override
    public String toString() {
        return "ResponseMessage{" +
                "message=" + message +
                ", correspondingId='" + correspondingId + '\'' +
                ", id='" + id + '\'' +
                ", signature=" + signature +
                '}';
    }
}
