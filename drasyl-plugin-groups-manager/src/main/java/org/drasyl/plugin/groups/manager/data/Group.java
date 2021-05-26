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
package org.drasyl.plugin.groups.manager.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.plugin.groups.client.GroupUri;

import java.time.Duration;
import java.util.Objects;

import static java.time.Duration.ofSeconds;
import static java.util.Objects.requireNonNull;
import static org.drasyl.util.Preconditions.requireNonNegative;
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
    public static final byte GROUP_DEFAULT_MIN_DIFFICULTY = (byte) 0;
    private final String name;
    private final String credentials;
    private final byte minDifficulty;
    private final Duration timeout;

    private Group(final String name,
                  final String credentials,
                  final byte minDifficulty,
                  final Duration timeout) {
        this.minDifficulty = requireNonNegative(minDifficulty, "minDifficulty must be non-negative");
        if (GROUP_MIN_TIMEOUT.compareTo(timeout) > 0) {
            throw new IllegalArgumentException("timeout must not be less than " + GROUP_MIN_TIMEOUT.toSeconds() + "s");
        }
        this.name = requireNonNull(name);
        this.credentials = requireNonNull(credentials);
        this.timeout = requireNonNull(timeout);
    }

    @SuppressWarnings("unused")
    @JsonCreator
    private Group(@JsonProperty("name") final String name,
                  @JsonProperty("credentials") final String credentials,
                  @JsonProperty("minDifficulty") final byte minDifficulty,
                  @JsonProperty("timeout") final long timeoutSeconds) {
        this(name, credentials, minDifficulty, ofSeconds(timeoutSeconds));
    }

    public String getName() {
        return name;
    }

    public String getCredentials() {
        return credentials;
    }

    public byte getMinDifficulty() {
        return minDifficulty;
    }

    @JsonIgnore
    public Duration getTimeout() {
        return timeout;
    }

    @JsonProperty("timeout")
    public long getTimeoutSeconds() {
        return timeout.toSeconds();
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

    public GroupUri getUri(final IdentityPublicKey manager) {
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
                           final byte minDifficulty,
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
