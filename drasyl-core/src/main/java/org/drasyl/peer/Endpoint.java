package org.drasyl.peer;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.drasyl.crypto.CryptoException;
import org.drasyl.identity.CompressedPublicKey;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

import static org.drasyl.util.UriUtil.overrideFragment;
import static org.drasyl.util.UriUtil.removeFragment;
import static org.drasyl.util.WebSocketUtil.isWebSocketSecureURI;
import static org.drasyl.util.WebSocketUtil.isWebSocketURI;
import static org.drasyl.util.WebSocketUtil.webSocketPort;

/**
 * Represents an endpoint of a drasyl node. This is a {@link URI} that must use the WebSocket
 * (Secure) protocol.
 * <p>
 * This is an immutable object.
 */
public class Endpoint implements Comparable<Endpoint> {
    private final URI uri;
    private final CompressedPublicKey publicKey;

    /**
     * Creates a new {@code Endpoint}.
     *
     * @param uri a drasyl node endpoint represented as {@code URI}
     * @throws NullPointerException     if {@code uri} is {@code null}
     * @throws IllegalArgumentException if {@code uri} is an invalid {@code Endpoint}
     */
    Endpoint(final URI uri, final CompressedPublicKey publicKey) {
        if (!isWebSocketURI(uri)) {
            throw new IllegalArgumentException("URI must use the WebSocket (Secure) protocol.");
        }
        this.uri = uri;
        this.publicKey = publicKey;
    }

    /**
     * Returns the {@link URI} of this {@code Endpoint}.
     *
     * @return a {@link URI} contained in this {@code Endpoint}
     */
    public URI getURI() {
        return uri;
    }

    /**
     * Returns the {@link CompressedPublicKey} of this {@code Endpoint}.
     *
     * @return a {@link CompressedPublicKey} if this {@code Endpoint} contains a public key.
     * Otherwise {@code null}
     */
    public CompressedPublicKey getPublicKey() {
        return publicKey;
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
        return Objects.equals(uri, endpoint.uri) &&
                Objects.equals(publicKey, endpoint.publicKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uri, publicKey);
    }

    @JsonValue
    @Override
    public String toString() {
        if (publicKey == null) {
            return uri.toString();
        }
        else {
            return overrideFragment(uri, publicKey.toString()).toString();
        }
    }

    /**
     * Returns the host component of this endpoint.
     *
     * @return The host component of this URI, or {@code null} if the host is undefined
     */
    public String getHost() {
        return uri.getHost();
    }

    /**
     * Returns the port of this endpoint.
     *
     * @return The port of this endpoint
     */
    public int getPort() {
        return webSocketPort(uri);
    }

    /**
     * Returns {@code true} if endpoint uses WebSocket Secure protocol. Otherwise {@code false}.
     *
     * @return {@code true} if endpoint uses WebSocket Secure protocol. Otherwise {@code false}
     */
    public boolean isSecureEndpoint() {
        return isWebSocketSecureURI(uri);
    }

    /**
     * Compares this {@code Endpoint} to another object, which must be a {@code Endpoint}.
     *
     * @param that The object to which this {@code Endpoint} is to be compared
     * @return A negative integer, zero, or a positive integer as this {@code Endpoint} is less
     * than, equal to, or greater than the given {@code Endpoint}
     */
    @Override
    public int compareTo(final Endpoint that) {
        return uri.compareTo(that.uri);
    }

    /**
     * Converts an {@link URI} and {@link CompressedPublicKey} into {@code Endpoint}.
     *
     * @param uri       uri component of the endpoint
     * @param publicKey public key component of the endpoint
     * @return {@code Endpoint} converted from {@code uri} and {@code publicKey}
     * @throws NullPointerException     if {@code uri} is {@code null}
     * @throws IllegalArgumentException if {@code uri} and {@code publicKey} creates an invalid {@code Endpoint}
     */
    public static Endpoint of(final URI uri, final CompressedPublicKey publicKey) {
        return new Endpoint(removeFragment(uri), publicKey);
    }

    /**
     * Converts an {@link String} and {@link CompressedPublicKey} into {@code Endpoint}.
     *
     * @param uri       uri component of the endpoint
     * @param publicKey public key component of the endpoint
     * @return {@code Endpoint} converted from {@code endpoint}
     * @throws NullPointerException     if {@code uri} is {@code null}
     * @throws IllegalArgumentException if {@code uri} and {@code publicKey} creates an invalid {@code Endpoint} or violates
     *                                  RFC&nbsp;2396
     */
    public static Endpoint of(final String uri, final CompressedPublicKey publicKey) {
        try {
            return of(new URI(uri), publicKey);
        }
        catch (final URISyntaxException x) {
            throw new IllegalArgumentException(x.getMessage(), x);
        }
    }

    /**
     * Converts an {@link URI} into {@code Endpoint}.
     *
     * @param endpoint a drasyl node endpoint represented as {@code URI}
     * @return {@code Endpoint} converted from {@code endpoint}
     * @throws NullPointerException     if {@code endpoint} is {@code null}
     * @throws IllegalArgumentException if {@code endpoint} is an invalid {@code Endpoint}
     */
    public static Endpoint of(final URI endpoint) {
        if (endpoint.getFragment() != null) {
            try {
                return of(endpoint, CompressedPublicKey.of(endpoint.getFragment()));
            }
            catch (final CryptoException e) {
                throw new IllegalArgumentException(e.getMessage(), e);
            }
        }
        else {
            return new Endpoint(endpoint, null);
        }
    }

    /**
     * Converts a {@link String} to a {@code Endpoint}.
     *
     * @param endpoint a drasyl node endpoint represented as {@code URI}
     * @return {@code Endpoint} converted from {@code endpoint}
     * @throws NullPointerException     if {@code endpoint} is {@code null}
     * @throws IllegalArgumentException if {@code endpoint} is an invalid {@code Endpoint} or
     *                                  violates RFC&nbsp;2396
     */
    @JsonCreator
    public static Endpoint of(final String endpoint) {
        try {
            return of(new URI(endpoint));
        }
        catch (final URISyntaxException x) {
            throw new IllegalArgumentException(x.getMessage(), x);
        }
    }
}
