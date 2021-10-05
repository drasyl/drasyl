/*
 * Copyright (c) 2020-2021 Heiko Bornholdt and Kevin Röbert
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.drasyl.handler.plugin.groups.client.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import org.drasyl.handler.plugin.groups.client.Group;

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
    public boolean equals(final Object o) {
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
