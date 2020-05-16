package org.drasyl.util;

import java.net.URI;

public class WebsocketUtil {
    private WebsocketUtil() {
        // util class
    }

    /**
     * Returns the port in the Websocket URI. If no custom port is specified, the protocol default
     * port is assumed. If no port could be determined, a {@link IllegalArgumentException} is
     * thrown.
     *
     * @param uri
     * @return
     */
    public static int websocketPort(URI uri) {
        int port = uri.getPort();

        // port was included in URI
        if (port != -1) {
            return port;
        }

        // Fallback: Use protocol standard ports
        String scheme = uri.getScheme();
        if (scheme != null) {
            if (scheme.equals("ws")) {
                return 80;
            }
            else if (scheme.equals("wss")) {
                return 443;
            }
        }

        throw new IllegalArgumentException("Unable to determine websocket port.");
    }

    /**
     * Returns <code>true</code> if <code>uri</code> is a Websocket Secure URI. Otherwise, returns
     * <code>false</code>.
     *
     * @param uri
     * @return
     */
    public static boolean isWebsocketSecureURI(URI uri) {
        return uri.getScheme() != null && uri.getScheme().equals("wss");
    }

    /**
     * Returns <code>true</code> if <code>uri</code> is a non-secure Websocket URI. Otherwise,
     * returns
     * <code>false</code>.
     *
     * @param uri
     * @return
     */
    public static boolean isWebsocketNonSecureURI(URI uri) {
        return uri.getScheme() != null && uri.getScheme().equals("ws");
    }

    /**
     * Returns <code>true</code> if <code>uri</code> is Websocket URI. Otherwise, returns
     * <code>false</code>.
     *
     * @param uri
     * @return
     */
    public static boolean isWebsocketURI(URI uri) {
        return isWebsocketNonSecureURI(uri) || isWebsocketURI(uri);
    }
}
