/*
 * Copyright (c) 2020
 *
 * This file is part of drasyl.
 *
 * drasyl is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * drasyl is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.drasyl.all.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Objects;

/**
 * An IP address with a host name and a port that can be used for easy
 * serialization.
 */
public class IPAddress {
    private final String host;
    private final int port;

    IPAddress() {
        host = null;
        port = 0;
    }

    /**
     * Creates an IP address with a host name and a port from an address string
     * where host and port are separated by ':'.
     * 
     * @param address the address which must contain a port separated by ':'
     * @throws IllegalArgumentException if the address doesn't contains a port or
     *                                  the port is invalid
     */
    @JsonCreator
    public IPAddress(String address) {
        if (!address.contains(":")) {
            throw new IllegalArgumentException("Address must contain a port");
        }

        int lastIndexOf = address.lastIndexOf(':');
        this.host = address.substring(0, lastIndexOf);
        this.port = Integer.valueOf(address.substring(lastIndexOf + 1));
    }

    /**
     * Creates an IP address with a host name and a port.
     * 
     * @param host the host name
     * @param port the port
     * @throws NullPointerException     if host is null
     * @throws IllegalArgumentException if the address doesn't contains a valid port
     */
    public IPAddress(String host, int port) {
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("Port must be a valid port.");
        }
        Objects.requireNonNull(host);
        this.host = host;
        this.port = port;
    }

    /**
     * @return the host
     */
    public String getHost() {
        return host;
    }

    /**
     * @return the port
     */
    public int getPort() {
        return port;
    }

    @Override
    @JsonValue
    public String toString() {
        return host + ":" + port;
    }

    @Override
    public int hashCode() {
        return Objects.hash(host, port);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof IPAddress) {
            IPAddress a2 = (IPAddress) o;

            return host.equals(a2.host) && port == a2.port;
        }

        return false;
    }
}
