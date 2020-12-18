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
package org.drasyl.remote.message;

import org.drasyl.DrasylNode;
import org.drasyl.util.UnsignedShort;

import java.io.IOException;
import java.util.Objects;
import java.util.Properties;

/**
 * This UserAgent is attached to each message and allows the recipient to learn about the
 * capabilities and configuration of the sender.
 */
public class UserAgent {
    private final UnsignedShort version;

    /**
     * Initializes a new UserAgent.
     *
     * @param version positive number in range [0, 2^16]
     * @throws IllegalArgumentException if not in range [0, 2^16]
     */
    UserAgent(final int version) {
        this.version = UnsignedShort.of(version);
    }

    /**
     * Initializes a new UserAgent.
     *
     * @param version version in big-endian format.
     */
    public UserAgent(final byte[] version) {
        this.version = UnsignedShort.of(version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(version);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final UserAgent userAgent = (UserAgent) o;
        return Objects.equals(version, userAgent.version);
    }

    /**
     * Generates a new user agent with drasyl version, operation system type/version, system
     * architecture, and java version used by this node.
     *
     * @return the generated user agent
     */
    public static UserAgent generate() {
        final Properties properties = new Properties();
        try {
            properties.load(DrasylNode.class.getClassLoader().getResourceAsStream("project.properties"));
            return new UserAgent(Integer.parseInt(properties.getProperty("protocol_version")));
        }
        catch (final IOException e) {
            return new UserAgent(0);
        }
    }

    public UnsignedShort getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return "UserAgent{" +
                "version=" + version +
                '}';
    }
}
