/*
 * Copyright (c) 2020-2021.
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
package org.drasyl.plugin.groups.client;

import org.drasyl.crypto.CryptoException;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.util.UriUtil;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

import static java.util.Optional.ofNullable;
import static org.drasyl.util.SecretUtil.maskSecret;

/**
 * This class models the connection/join setting of a group.
 * <p>
 * This is an immutable object.
 */
@SuppressWarnings({ "java:S1192" })
public class GroupUri {
    public static final int MIN_TIMEOUT = 60;
    public static final String SCHEME = "groups";
    private final CompressedPublicKey manager;
    private final String credentials;
    private final String name;
    private final Duration timeout;

    private GroupUri(final CompressedPublicKey manager,
                     final String credentials,
                     final String name,
                     final Duration timeout) {
        this.manager = manager;
        this.credentials = credentials;
        this.name = name;
        this.timeout = timeout;
    }

    public CompressedPublicKey getManager() {
        return manager;
    }

    public String getCredentials() {
        return credentials;
    }

    public String getName() {
        return name;
    }

    public Duration getTimeout() {
        return timeout;
    }

    @Override
    public int hashCode() {
        return Objects.hash(manager, credentials, name, timeout);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final GroupUri options = (GroupUri) o;
        return Objects.equals(manager, options.manager) &&
                Objects.equals(credentials, options.credentials) &&
                Objects.equals(name, options.name) &&
                Objects.equals(timeout, options.timeout);
    }

    @Override
    public String toString() {
        return UriUtil.createUri(SCHEME, maskSecret(credentials), manager.toString(), -1, "/" + name, "timeout=" + timeout.toSeconds()).toString();
    }

    public URI toUri() {
        return UriUtil.createUri(SCHEME, credentials, manager.toString(), -1, "/" + name, "timeout=" + timeout.toSeconds());
    }

    public Group getGroup() {
        return Group.of(name);
    }

    public static GroupUri of(final CompressedPublicKey manager,
                              final String credentials,
                              final String name,
                              final Duration timeout) {
        return new GroupUri(manager, credentials, name, timeout);
    }

    /**
     * Generates a {@link GroupUri} object from the given groups URI.
     *
     * @param uri the groups URI
     * @return a {@link GroupUri} object
     * @throws IllegalArgumentException if the groups URI is invalid
     */
    public static GroupUri of(final String uri) {
        return of(URI.create(uri));
    }

    /**
     * Generates a {@link GroupUri} object from the given groups URI.
     *
     * @param uri the groups URI
     * @return a {@link GroupUri} object
     * @throws IllegalArgumentException if the groups URL is invalid
     */
    public static GroupUri of(final URI uri) {
        if (uri.getScheme() == null || !uri.getScheme().equals(SCHEME)) {
            throw new IllegalArgumentException("Scheme must be " + SCHEME);
        }
        if (uri.getHost() == null) {
            throw new IllegalArgumentException("Host must not be null");
        }
        if (uri.getRawPath() == null) {
            throw new IllegalArgumentException("Path must not be null");
        }
        final String name = uri.getRawPath().replace("/", "");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Path must not be blank");
        }

        try {
            final CompressedPublicKey manager = CompressedPublicKey.of(uri.getHost());
            final String credentials = ofNullable(uri.getUserInfo()).orElse("");
            final Map<String, String> queryMap = UriUtil.getQueryMap(uri);

            long timeoutSeconds = MIN_TIMEOUT;
            if (queryMap.containsKey("timeout")) {
                timeoutSeconds = Long.parseLong(queryMap.get("timeout"));
            }
            if (timeoutSeconds < MIN_TIMEOUT) {
                throw new IllegalArgumentException("Timeout must be greater than " + MIN_TIMEOUT + "s.");
            }
            final Duration timeout = Duration.ofSeconds(timeoutSeconds);

            return new GroupUri(manager, credentials, name, timeout);
        }
        catch (final CryptoException e) {
            throw new IllegalArgumentException("Host contains invalid public key: ", e);
        }
        catch (final NumberFormatException e) {
            throw new IllegalArgumentException("The given URI '" + uri + "' contains an invalid timeout value: ", e);
        }
    }
}
