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
package org.drasyl.util;

import java.net.URI;

/**
 * Utility class for operations on websocket {@link URI}s (e.g. ws://foo.bar).
 */
public final class WebSocketUtil {
    public static final int WS_PORT = 80;
    public static final int WSS_PORT = 443;

    private WebSocketUtil() {
        // util class
    }

    /**
     * @param uri the URI to be used for determining the WebSocket URI
     * @return the port in the WebSocket URI. If no custom port is specified, the protocol default
     * port is assumed
     * @throws IllegalArgumentException if no port could be determined
     * @throws NullPointerException     if {@code uri} is {@code null}
     */
    public static int webSocketPort(final URI uri) {
        final int port = uri.getPort();

        // port was included in URI
        if (port != -1) {
            return port;
        }

        // Fallback: Use protocol standard ports
        final String scheme = uri.getScheme();
        if (scheme != null) {
            if ("ws".equals(scheme)) {
                return WS_PORT;
            }
            else if ("wss".equals(scheme)) {
                return WSS_PORT;
            }
        }

        throw new IllegalArgumentException("Unable to determine websocket port for uri '" + uri + "'. Not a websocket uri?");
    }

    /**
     * @param uri the URI to be checked
     * @return <code>true</code> if <code>uri</code> is a WebSocket Secure URI. Otherwise, returns
     * <code>false</code>
     * @throws NullPointerException if {@code uri} is {@code null}
     */
    public static boolean isWebSocketSecureURI(final URI uri) {
        return "wss".equals(uri.getScheme());
    }

    /**
     * @param uri the URI to be checked
     * @return <code>true</code> if <code>uri</code> is a non-secure Websocket URI. Otherwise,
     * returns <code>false</code>
     * @throws NullPointerException if {@code uri} is {@code null}
     */
    public static boolean isWebSocketNonSecureURI(final URI uri) {
        return "ws".equals(uri.getScheme());
    }

    /**
     * @param uri the URI to be checked
     * @return <code>true</code> if <code>uri</code> is Websocket URI. Otherwise, returns
     * <code>false</code>
     * @throws NullPointerException if {@code uri} is {@code null}
     */
    public static boolean isWebSocketURI(final URI uri) {
        return isWebSocketNonSecureURI(uri) || isWebSocketSecureURI(uri);
    }
}
