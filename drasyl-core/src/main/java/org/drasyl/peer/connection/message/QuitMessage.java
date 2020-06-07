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

/**
 * A message representing a termination of a connection.
 */
public class QuitMessage extends AbstractMessage implements RequestMessage {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final CloseReason reason;

    public QuitMessage() {
        this(null);
    }

    public QuitMessage(CloseReason reason) {
        this.reason = reason;
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
        QuitMessage that = (QuitMessage) o;
        return Objects.equals(reason, that.reason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), reason);
    }

    @Override
    public String toString() {
        return "QuitMessage{" +
                "reason='" + reason + '\'' +
                ", id='" + id + '\'' +
                ", signature=" + signature +
                '}';
    }

    public CloseReason getReason() {
        return reason;
    }

    /**
     * Specifies the reason for closing the connection.
     */
    public enum CloseReason {
        REASON_NEW_SESSION("New Connection with this Identity has been created."),
        REASON_SHUTTING_DOWN("Peer is shutting down.");
        private static final Map<String, CloseReason> reasons = new HashMap<>();

        static {
            for (CloseReason description : values()) {
                reasons.put(description.getDescription(), description);
            }
        }

        private final String description;

        CloseReason(String description) {
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
        public static CloseReason from(String description) {
            return reasons.get(description);
        }
    }
}
