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
package org.drasyl.plugin.groups.manager.data;

import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.plugin.groups.client.GroupUri;

import java.time.Duration;
import java.util.Objects;

import static java.time.Duration.ofSeconds;
import static org.drasyl.util.SecretUtil.maskSecret;

/**
 * Class is used to model the state of a group.
 * <p>
 * <b>This class should only plugin internally used.</b>
 * </p>
 */
public class Group {
    public static final Duration GROUP_MIN_TIMEOUT = ofSeconds(60);
    public static final Duration GROUP_DEFAULT_TIMEOUT = GROUP_MIN_TIMEOUT;
    public static final short GROUP_DEFAULT_MIN_DIFFICULTY = 0;
    private final String name;
    private final String credentials;
    private final short minDifficulty;
    private final Duration timeout;

    private Group(final String name,
                  final String credentials,
                  final short minDifficulty,
                  final Duration timeout) {
        if (minDifficulty < 0) {
            throw new IllegalArgumentException("minDifficulty must be non-negative");
        }
        if (GROUP_MIN_TIMEOUT.compareTo(timeout) > 0) {
            throw new IllegalArgumentException("timeout must not be less than " + GROUP_MIN_TIMEOUT.toSeconds() + "s");
        }
        this.name = Objects.requireNonNull(name);
        this.credentials = Objects.requireNonNull(credentials);
        this.minDifficulty = minDifficulty;
        this.timeout = Objects.requireNonNull(timeout);
    }

    public String getName() {
        return name;
    }

    public String getCredentials() {
        return credentials;
    }

    public short getMinDifficulty() {
        return minDifficulty;
    }

    public Duration getTimeout() {
        return timeout;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, credentials, minDifficulty, timeout);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Group that = (Group) o;
        return minDifficulty == that.minDifficulty &&
                Objects.equals(name, that.name) &&
                Objects.equals(credentials, that.credentials) &&
                Objects.equals(timeout, that.timeout);
    }

    @Override
    public String toString() {
        return "Group{" +
                "name='" + name + '\'' +
                ", credentials='" + maskSecret(credentials) + '\'' +
                ", minDifficulty=" + minDifficulty +
                ", timeout=" + timeout +
                '}';
    }

    public GroupUri getUri(final CompressedPublicKey manager) {
        return GroupUri.of(manager, credentials, name, timeout);
    }

    /**
     * Creates a Group object with given parameters.
     *
     * @param name          name of group
     * @param credentials   credentials of group
     * @param minDifficulty min difficulty of group
     * @param timeout       timeout of group
     * @return created Group
     * @throws IllegalArgumentException if created Group is invalid
     */
    public static Group of(final String name,
                           final String credentials,
                           final short minDifficulty,
                           final Duration timeout) {
        return new Group(name, credentials, minDifficulty, timeout);
    }

    /**
     * Creates a Group object with default minDifficulty and timeout.
     *
     * @param name        name of group
     * @param credentials credentials of group
     * @return created Group
     * @throws IllegalArgumentException if created Group is invalid
     */
    public static Group of(final String name,
                           final String credentials) {
        return of(name, credentials, GROUP_DEFAULT_MIN_DIFFICULTY, GROUP_DEFAULT_TIMEOUT);
    }
}
