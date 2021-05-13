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
package org.drasyl.peer;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.drasyl.identity.IdentityPublicKey;
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
    private final IdentityPublicKey publicKey;
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
                     final IdentityPublicKey publicKey,
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
    private Endpoint(final String host, final int port, final IdentityPublicKey publicKey) {
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
     * Returns the {@link IdentityPublicKey} of this {@code Endpoint}.
     *
     * @return The public key of this endpoint.
     */
    public IdentityPublicKey getIdentityPublicKey() {
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
                              final IdentityPublicKey publicKey,
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
                              final IdentityPublicKey publicKey) {
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

        return of(endpoint.getHost(), endpoint.getPort(), IdentityPublicKey.of(publicKey), networkId);
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
