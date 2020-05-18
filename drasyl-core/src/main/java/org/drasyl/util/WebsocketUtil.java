/*
 * Copyright (c) 2020.
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
