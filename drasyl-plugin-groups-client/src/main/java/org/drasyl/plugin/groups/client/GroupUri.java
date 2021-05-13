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
package org.drasyl.plugin.groups.client;

import org.drasyl.identity.IdentityPublicKey;
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
    private final IdentityPublicKey manager;
    private final String credentials;
    private final String name;
    private final Duration timeout;

    private GroupUri(final IdentityPublicKey manager,
                     final String credentials,
                     final String name,
                     final Duration timeout) {
        this.manager = manager;
        this.credentials = credentials;
        this.name = name;
        this.timeout = timeout;
    }

    public IdentityPublicKey getManager() {
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

    public static GroupUri of(final IdentityPublicKey manager,
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
            final IdentityPublicKey manager = IdentityPublicKey.of(uri.getHost());
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
        catch (final NumberFormatException e) {
            throw new IllegalArgumentException("The given URI '" + uri + "' contains an invalid timeout value: ", e);
        }
    }
}
