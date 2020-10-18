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

import static org.drasyl.plugin.groups.manager.GroupsManagerConfig.GROUP_DEFAULT_MIN_DIFFICULTY;
import static org.drasyl.plugin.groups.manager.GroupsManagerConfig.GROUP_DEFAULT_TIMEOUT;
import static org.drasyl.util.SecretUtil.maskSecret;

/**
 * Class is used to model the state of a group.
 * <p>
 * <b>This class should only plugin internally used.</b>
 * </p>
 */
public class Group {
    private final String name;
    private final String credentials;
    private final short minDifficulty;
    private final Duration timeout;

    private Group(final String name,
                  final String credentials,
                  final short minDifficulty,
                  final Duration timeout) {
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

    public static Group of(final String name,
                           final String secret,
                           final short minDifficulty,
                           final Duration timeout) {
        return new Group(name, secret, minDifficulty, timeout);
    }

    public static Group of(final String name,
                           final String secret) {
        return of(name, secret, GROUP_DEFAULT_MIN_DIFFICULTY, GROUP_DEFAULT_TIMEOUT);
    }
}
