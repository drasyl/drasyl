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
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * A message representing an exception. Such an exception should always be handled.
 */
@SuppressWarnings({ "squid:S2166", "common-java:DuplicatedBlocks" })
public class ExceptionMessage extends AbstractMessage {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final Error error;

    @JsonCreator
    private ExceptionMessage(@JsonProperty("id") MessageId id,
                             @JsonProperty("error") Error error) {
        super(id);
        this.error = requireNonNull(error);
    }

    /**
     * Creates a new exception message.
     *
     * @param error the error type
     */
    public ExceptionMessage(Error error) {
        super();
        this.error = requireNonNull(error);
    }

    /**
     * @return the error
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
        ExceptionMessage that = (ExceptionMessage) o;
        return Objects.equals(error, that.error);
    }

    @Override
    public String toString() {
        return "ExceptionMessage{" +
                "error='" + error + '\'' +
                ", id='" + id +
                '}';
    }

    /**
     * Specifies the type of the {@link ExceptionMessage}.
     */
    public enum Error {
        ERROR_INTERNAL("Internal Error occurred."),
        ERROR_FORMAT("Invalid Message format.");
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