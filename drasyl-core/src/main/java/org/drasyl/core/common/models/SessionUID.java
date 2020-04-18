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

package org.drasyl.core.common.models;

import org.drasyl.core.common.util.random.RandomUtil;
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.re2j.Pattern;

import java.io.Serializable;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Arrays.stream;
import static java.util.Objects.requireNonNull;

/**
 * Represents a unique session identifier
 */
@SuppressWarnings({"squid:S4144"})

public final class SessionUID implements Serializable {
    private static final long serialVersionUID = -4394276525441246419L;
    private static final Pattern syntax = Pattern.compile("^[a-zA-Z0-9*?-]+(#[a-zA-Z0-9*?-]+)*$");
    /**
     * Denotes a SessionUID that is applicable to all systems
     */
    public static final SessionUID ALL = new SessionUID("*");
    /**
     * Denotes a SessionUID that is applicable to an arbitrary system
     */
    public static final SessionUID ANY = new SessionUID("?");
    /**
     * Denotes a delimiter for multicast SessionUIDs
     */
    public static final String MULTICAST_DELIMITER = "#";
    @JsonValue
    private final String value;

    private SessionUID() {
        value = null;
    }

    private SessionUID(String internal) {
        if (!isValid(requireNonNull(internal)))
            throw new IllegalArgumentException("This is not a valid SessionUID: '" + internal + "'");

        this.value = internal;
    }

    /**
     * SessionUID from a string value
     */
    public static SessionUID of(String value) {
        return new SessionUID(value);
    }

    /**
     * Multicast SessionUID from a list of string values
     */
    public static SessionUID of(String... values) {
        return SessionUID.of(String.join(MULTICAST_DELIMITER, values));
    }

    /**
     * Multicast SessionUID from a list of SessionUID values
     */
    public static SessionUID of(SessionUID... values) {
        return of(stream(values).map(SessionUID::getValue).collect(Collectors.joining(MULTICAST_DELIMITER)));
    }

    /**
     * SessionUID from random a secure random value
     */
    public static SessionUID random() {
        return of(RandomUtil.randomString(16));
    }

    /**
     * Returns the underlying string representation
     */
    public String getStringValue() {
        return value;
    }

    /**
     * Returns the underlying string representation
     */
    public String getValue() {
        return value;
    }

    /**
     * Returns true if the given value is a valid SessionUID.
     *
     * @param value the value
     */
    public boolean isValid(String value) {
        return syntax.matches(value);
    }

    /**
     * Returns a set of all SessionUIDs, if this SessionUID is not a multicast, this set only contains one element.
     */
    public Set<SessionUID> getUIDs() {
        return stream(value.split(MULTICAST_DELIMITER)).map(SessionUID::of).collect(Collectors.toSet());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof SessionUID) {
            SessionUID that = (SessionUID) obj;
            return Objects.equals(this.value, that.value);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return "SessionUID[" + value + "]";
    }

}
