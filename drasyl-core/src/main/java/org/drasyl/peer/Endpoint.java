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
package org.drasyl.peer;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.pipeline.address.InetSocketAddressWrapper;
import org.drasyl.util.UriUtil;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * Represents an endpoint of a drasyl node. This is a {@link URI} that must use the WebSocket
 * (Secure) protocol.
 * <p>
 * This is an immutable object.
 */
public class Endpoint {
    private static final int MAX_PORT = 65536;
    private final String host;
    private final int port;
    private final CompressedPublicKey publicKey;
    private final Integer networkId;

    /**
     * Creates a new {@code Endpoint}.
     *
     * @param host      the hostname part of the endpoint
     * @param port      the port number of the endpoint
     * @param publicKey the public key of the endpoint
     * @param networkId the network id of the endpoint
     * @throws NullPointerException     if {@code host} or {@code publicKey} is {@code null}
     * @throws IllegalArgumentException if {@code port} is out of range [0,65536]
     */
    private Endpoint(final String host,
                     final int port,
                     final CompressedPublicKey publicKey,
                     final Integer networkId) {
        this.host = requireNonNull(host);
        this.port = port;
        if (port < -1 || port > MAX_PORT) {
            throw new IllegalArgumentException("port out of range: " + port);
        }
        this.publicKey = requireNonNull(publicKey);
        this.networkId = networkId;
    }

    /**
     * Creates a new {@code Endpoint}.
     *
     * @param host      the hostname part of the endpoint
     * @param port      the port number of the endpoint
     * @param publicKey the public key of the endpoint
     * @throws NullPointerException     if {@code host} or {@code publicKey} is {@code null}
     * @throws IllegalArgumentException if {@code port} is out of range [0,65536]
     */
    private Endpoint(final String host, final int port, final CompressedPublicKey publicKey) {
        this(host, port, publicKey, null);
    }

    /**
     * Returns an {@link URI} representing this {@code Endpoint}.
     *
     * @return The {@link URI} representing this {@code Endpoint}.
     * @throws IllegalArgumentException If the created {@link URI} violates RFC&nbsp;2396
     */
    public URI getURI() {
        return URI.create("udp://" + host + ":" + port + "?publicKey=" + publicKey + (networkId != null ? ("&networkId=" + networkId) : ""));
    }

    /**
     * Returns the hostname of this endpoint.
     *
     * @return The hostname of this endpoint.
     */
    public String getHost() {
        return host;
    }

    /**
     * Returns the port of this endpoint.
     *
     * @return The port of this endpoint
     */
    public int getPort() {
        return port;
    }

    /**
     * Returns the {@link CompressedPublicKey} of this {@code Endpoint}.
     *
     * @return The public key of this endpoint.
     */
    public CompressedPublicKey getPublicKey() {
        return publicKey;
    }

    /**
     * Returns the network id of this endpoint.
     *
     * @return The network id of this endpoint
     */
    public Integer getNetworkId() {
        return networkId;
    }

    /**
     * @throws IllegalArgumentException if the port parameter is outside the range of valid port
     *                                  values, or if the hostname parameter is {@code null}.
     */
    public InetSocketAddressWrapper toInetSocketAddress() {
        return new InetSocketAddressWrapper(host, port);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Endpoint endpoint = (Endpoint) o;
        return port == endpoint.port && Objects.equals(host, endpoint.host) && Objects.equals(publicKey, endpoint.publicKey) && Objects.equals(networkId, endpoint.networkId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(host, port, publicKey, networkId);
    }

    @JsonValue
    @Override
    public String toString() {
        return getURI().toString();
    }

    /**
     * Converts the given {@code host}, {@code port}, {@code publicKey}, and {@code networkId} into
     * an {@code Endpoint}.
     *
     * @param host      the hostname part of the endpoint
     * @param port      the port number of the endpoint
     * @param publicKey the public key of the endpoint
     * @param networkId the network id of the endpoint
     * @return {@code Endpoint} converted from {@code endpoint}
     * @throws NullPointerException     if {@code endpoint} is {@code null} or contains no public
     *                                  key
     * @throws IllegalArgumentException if {@code host}, {@code port}, and {@code publicKey} creates
     *                                  an invalid {@code Endpoint}
     */
    public static Endpoint of(final String host,
                              final int port,
                              final CompressedPublicKey publicKey,
                              final Integer networkId) {
        return new Endpoint(host, port, publicKey, networkId);
    }

    /**
     * Converts the given {@code host}, {@code port}, and {@code publicKey} into an {@code
     * Endpoint}.
     *
     * @param host      the hostname part of the endpoint
     * @param port      the port number of the endpoint
     * @param publicKey the public key of the endpoint
     * @return {@code Endpoint} converted from {@code endpoint}
     * @throws NullPointerException     if {@code endpoint} is {@code null} or contains no public
     *                                  key
     * @throws IllegalArgumentException if {@code host}, {@code port}, and {@code publicKey} creates
     *                                  an invalid {@code Endpoint}
     */
    public static Endpoint of(final String host,
                              final int port,
                              final CompressedPublicKey publicKey) {
        return new Endpoint(host, port, publicKey);
    }

    /**
     * Converts an {@link URI} into an {@code Endpoint}.
     *
     * @param endpoint a drasyl node endpoint represented as {@code URI}
     * @return {@code Endpoint} converted from {@code endpoint}
     * @throws NullPointerException     if {@code endpoint} is {@code null} or contains no public
     *                                  key
     * @throws IllegalArgumentException if {@code endpoint} creates an invalid {@code Endpoint}
     */
    public static Endpoint of(final URI endpoint) {
        if (!isUdpURI(endpoint)) {
            throw new IllegalArgumentException("URI must use the UDP protocol.");
        }
        if (endpoint.getPort() == -1) {
            throw new IllegalArgumentException("URI must contain port.");
        }
        final Map<String, String> queryMap = UriUtil.getQueryMap(endpoint);
        final String publicKey = queryMap.get("publicKey");
        if (publicKey == null) {
            throw new IllegalArgumentException("URI must contain public key.");
        }
        final String networkIdString = queryMap.get("networkId");
        final Integer networkId;
        if (networkIdString != null) {
            networkId = Integer.valueOf(networkIdString);
        }
        else {
            networkId = null;
        }

        return of(endpoint.getHost(), endpoint.getPort(), CompressedPublicKey.of(publicKey), networkId);
    }

    /**
     * Converts a {@link String} into an {@code Endpoint}.
     *
     * @param endpoint a drasyl node endpoint represented as {@code URI}
     * @return {@code Endpoint} converted from {@code endpoint}
     * @throws NullPointerException     if {@code endpoint} is {@code null}
     * @throws IllegalArgumentException if {@code endpoint} creates an invalid {@code Endpoint}
     */
    @JsonCreator
    public static Endpoint of(final String endpoint) {
        try {
            return of(new URI(endpoint));
        }
        catch (final URISyntaxException x) {
            throw new IllegalArgumentException("Invalid uri", x);
        }
    }

    private static boolean isUdpURI(final URI uri) {
        return "udp".equals(uri.getScheme());
    }
}
