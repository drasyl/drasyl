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

import java.time.Duration;
import java.util.Objects;

import static org.drasyl.util.SecretUtil.maskSecret;

/**
 * Class is used to model the state of a group.
 * <p>
 * <b>This class should only plugin internally used.</b>
 * </p>
 */
public class Group {
    private final String name;
    private final String secret;
    private final short minDifficulty;
    private final Duration timeout;

    private Group(final String name,
                  final String secret,
                  final short minDifficulty,
                  final Duration timeout) {
        this.name = Objects.requireNonNull(name);
        this.secret = Objects.requireNonNull(secret);
        this.minDifficulty = minDifficulty;
        this.timeout = Objects.requireNonNull(timeout);
    }

    public String getName() {
        return name;
    }

    public String getSecret() {
        return secret;
    }

    public short getMinDifficulty() {
        return minDifficulty;
    }

    public Duration getTimeout() {
        return timeout;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, secret, minDifficulty, timeout);
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
                Objects.equals(secret, that.secret) &&
                Objects.equals(timeout, that.timeout);
    }

    @Override
    public String toString() {
        return "Group{" +
                "name='" + name + '\'' +
                ", secret='" + maskSecret(secret) + '\'' +
                ", minDifficulty=" + minDifficulty +
                ", timeout=" + timeout +
                '}';
    }

    public static Group of(final String name,
                           final String secret,
                           final short minDifficulty,
                           final Duration timeout) {
        return new Group(name, secret, minDifficulty, timeout);
    }
}
