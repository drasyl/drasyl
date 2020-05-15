package org.drasyl.core.common.util;

import java.net.URI;

public class WebsocketUtil {
    private WebsocketUtil() {
        // util class
    }

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
                return  80;
            }
            else if (scheme.equals("wss")) {
                return 443;
            }
        }

        throw new IllegalArgumentException("Unable to determine websocket port.");
    }
}
