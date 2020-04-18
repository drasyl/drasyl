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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.io.Serializable;
import java.util.Objects;

@SuppressWarnings({ "squid:S4144" })
public class SessionChannel implements Serializable {
    private static final long serialVersionUID = 7889933568032644268L;

    private final String value;

    private SessionChannel() {
        this.value = null;
    }

    private SessionChannel(String internal) {
        this.value = Objects.requireNonNull(internal);
    }

    /**
     * Returns Channel from a string value.
     */
    @JsonCreator
    public static SessionChannel of(String value) {
        return new SessionChannel(value);
    }

    /**
     * Returns the underlying string representation
     */
    @JsonValue
    public String getStringValue() {
        return value;
    }

    /**
     * Returns the underlying string representation
     */
    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof SessionChannel) {
            SessionChannel that = (SessionChannel) obj;
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
        return "SessionChannel[" + value + "]";
    }
}
