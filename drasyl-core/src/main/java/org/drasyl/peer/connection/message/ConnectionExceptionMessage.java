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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * A message representing an exception that refers to a connection. The connection should be
 * terminated after such a message. Such an exception should always be handled.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class ConnectionExceptionMessage extends AbstractMessage implements RequestMessage {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final Error error;

    ConnectionExceptionMessage() {
        error = null;
    }

    /**
     * Creates a new exception message.
     *
     * @param error the exception type
     */
    public ConnectionExceptionMessage(Error error) {
        this.error = requireNonNull(error);
    }

    /**
     * @return the exception
     */
    public Error getError() {
        return error;
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
        ConnectionExceptionMessage that = (ConnectionExceptionMessage) o;
        return Objects.equals(error, that.error);
    }

    @Override
    public String toString() {
        return "ConnectionExceptionMessage{" +
                "type='" + error + '\'' +
                ", id='" + id + '\'' +
                ", signature=" + signature +
                '}';
    }

    /**
     * Specifies the type of the {@link ConnectionExceptionMessage}.
     */
    public enum Error {
        CONNECTION_ERROR_INITIALIZATION("Error occurred during initialization stage."),
        CONNECTION_ERROR_INTERNAL("Internal Error occurred."),
        CONNECTION_ERROR_HANDSHAKE("Handshake did not take place within timeout."),
        CONNECTION_ERROR_PING_PONG("Too many Ping Messages were not answered with a Pong Message."),
        CONNECTION_ERROR_SAME_PUBLIC_KEY("Peer has sent same Public Key. You can't connect to yourself. This would cause a routing loop. This could indicate a configuration error."),
        CONNECTION_ERROR_WRONG_PUBLIC_KEY("Peer has sent an unexpected Public Key. This could indicate a configuration error or man-in-the-middle attack.");
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
