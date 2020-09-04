package org.drasyl.peer;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

import static org.drasyl.util.WebSocketUtil.isWebSocketSecureURI;
import static org.drasyl.util.WebSocketUtil.isWebSocketURI;
import static org.drasyl.util.WebSocketUtil.webSocketPort;

/**
 * Represents an endpoint of a drasyl node. This is a {@link URI} that must use the WebSocket
 * (Secure) protocol.
 */
public class Endpoint implements Comparable<Endpoint> {
    @JsonValue
    private final URI uri;

    /**
     * Creates a new {@code Endpoint}.
     *
     * @param uri a drasyl node endpoint represented as {@code URI}
     * @throws NullPointerException     if {@code uri} is {@code null}
     * @throws IllegalArgumentException if {@code uri} is an invalid {@code Endpoint}
     */
    @JsonCreator
    Endpoint(URI uri) {
        if (!isWebSocketURI(uri)) {
            throw new IllegalArgumentException("URI must use the WebSocket (Secure) protocol.");
        }
        this.uri = uri;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Endpoint endpoint = (Endpoint) o;
        return Objects.equals(uri, endpoint.uri);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uri);
    }

    /**
     * Converts this {@code Endpoint} to an {@link URI}.
     *
     * @return a {@link URI} representing this {@code endpoint}
     */
    public URI toURI() {
        return uri;
    }

    @Override
    public String toString() {
        return uri.toString();
    }

    /**
     * Returns the host component of this endpont.
     *
     * @return The host component of this URI, or {@code null} if the host is undefined
     */
    public String getHost() {
        return uri.getHost();
    }

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
    public int compareTo(Endpoint that) {
        return uri.compareTo(that.uri);
    }

    /**
     * Converts an {@link URI} into {@code Endpoint}.
     *
     * @param endpoint a drasyl node endpoint represented as {@code URI}
     * @return {@code Endpoint} converted from {@code endpoint}
     * @throws NullPointerException     if {@code endpoint} is {@code null}
     * @throws IllegalArgumentException if {@code endpoint} is an invalid {@code Endpoint}
     */
    public static Endpoint of(URI endpoint) {
        return new Endpoint(endpoint);
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
    public static Endpoint of(String endpoint) {
        try {
            return of(new URI(endpoint));
        }
        catch (URISyntaxException x) {
            throw new IllegalArgumentException(x.getMessage(), x);
        }
    }
}
