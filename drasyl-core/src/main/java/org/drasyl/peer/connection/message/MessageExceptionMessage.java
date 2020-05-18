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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;
import org.drasyl.peer.connection.message.action.MessageAction;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * A message representing an exception that refers to a message. Such an exception should always be
 * handled.
 */
@SuppressWarnings({ "squid:S2166", "common-java:DuplicatedBlocks" })
public class MessageExceptionMessage extends AbstractResponseMessage<RequestMessage<?>, MessageExceptionMessage> implements UnrestrictedPassableMessage {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final Error error;

    MessageExceptionMessage() {
        super();
        error = null;
    }

    /**
     * Creates a new exception message.
     *
     * @param error           the error type
     * @param correspondingId
     */
    public MessageExceptionMessage(Error error, String correspondingId) {
        super(correspondingId);
        this.error = requireNonNull(error);
    }

    /**
     * @return the error
     */
    public Error getError() {
        return error;
    }

    @Override
    public MessageAction<MessageExceptionMessage> getAction() {
        return null;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), error);
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
        MessageExceptionMessage that = (MessageExceptionMessage) o;
        return Objects.equals(error, that.error);
    }

    @Override
    public String toString() {
        return "MessageExceptionMessage{" +
                "error='" + error + '\'' +
                ", correspondingId='" + correspondingId + '\'' +
                ", id='" + id + '\'' +
                ", signature=" + signature +
                '}';
    }

    /**
     * Specifies the type of the {@link MessageExceptionMessage}.
     */
    public enum Error {
        MESSAGE_ERROR_ALREADY_JOINED("This client has already an open Session with this Node Server. No need to authenticate twice.");
        private static final Map<String, Error> errors = new HashMap<>();

        static {
            for (Error description : values()) {
                errors.put(description.getDescription(), description);
            }
        }

        private final String description;

        Error(String description) {
            this.description = description;
        }

        /**
         * @return a human readable representation of the reason.
         */
        @JsonValue
        public String getDescription() {
            return description;
        }

        @JsonCreator
        public static Error from(String description) {
            return errors.get(description);
        }
    }
}
