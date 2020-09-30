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
 *  along with drasyl.  If not, see <http://www.gnu.org/licenses />.
 */
package org.drasyl.plugin.groups.client.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import org.drasyl.plugin.groups.client.Group;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * This message is sent by the groups server to the client when the join to a group was not
 * successful.
 * <p>
 * This is an immutable object.
 */
public class GroupJoinFailedMessage extends GroupActionMessage implements GroupsServerMessage {
    private final Error reason;

    @JsonCreator
    public GroupJoinFailedMessage(@JsonProperty("group") final Group group,
                                  @JsonProperty("reason") final Error reason) {
        super(group);
        this.reason = requireNonNull(reason);
    }

    public Error getReason() {
        return reason;
    }

    @Override
    public String toString() {
        return "GroupJoinFailedMessage{" +
                "group='" + group + '\'' +
                ", reason=" + reason +
                '}';
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
        final GroupJoinFailedMessage that = (GroupJoinFailedMessage) o;
        return reason == that.reason;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), reason);
    }

    /**
     * Specifies the reason of the {@link GroupJoinFailedMessage}.
     */
    public enum Error {
        ERROR_PROOF_TO_WEAK("The given proof of work is to weak for this group."),
        ERROR_UNKNOWN("An unknown error is occurred during join."),
        ERROR_GROUP_NOT_FOUND("The given group was not found.");
        private static final Map<String, Error> errors = new HashMap<>();

        static {
            for (final Error description : values()) {
                errors.put(description.getDescription(), description);
            }
        }

        private final String description;

        Error(final String description) {
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
        public static Error from(final String description) {
            return errors.get(description);
        }
    }
}
