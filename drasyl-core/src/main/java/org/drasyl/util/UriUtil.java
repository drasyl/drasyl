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
import java.net.URISyntaxException;

/**
 * Utility class for operations on {@link URI}s.
 */
public class UriUtil {
    private UriUtil() {
        // util class
    }

    /**
     * Creates a URI by parsing the given components.
     *
     * <p> This convenience factory method works as if by invoking the {@link
     * URI(String, String, String, int, String, String, String)} constructor; any {@link
     * URISyntaxException} thrown by the constructor is caught and wrapped in a new {@link
     * IllegalArgumentException} object, which is then thrown.
     *
     * <p> This method is provided for use in situations where it is known that
     * the given components results in a legal URI, for example for URI constants declared within a
     * program, and so it would be considered a programming error for the given components not to
     * parse as such. The constructors, which throw {@link URISyntaxException} directly, should be
     * used in situations where a URI is being constructed from user input or from some other source
     * that may be prone to errors.  </p>
     *
     * @param scheme Scheme name The scheme to be parsed into a URL
     * @param host   Host name The host to be parsed into a URL
     * @param port   Port number The port to be parsed into a URL
     * @return The new URI
     * @throws IllegalArgumentException If the URI constructed from the given components violates
     *                            RFC&nbsp;2396
     */
    public static URI createUri(String scheme, String host, int port) {
        try {
            return new URI(scheme, null, host, port, null, null, null);
        }
        catch (URISyntaxException x) {
            throw new IllegalArgumentException(x.getMessage(), x);
        }
    }

    /**
     * This method sets the port in <code>uri</code> to <code>port</code> and returns the resulting
     * URI.
     *
     * @param uri  the base URI
     * @param port the port
     * @return a combined URI and port URI
     * @throws IllegalArgumentException if resulting URI violates RFC&nbsp;2396
     */
    public static URI overridePort(URI uri, int port) {
        try {
            return new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), port, uri.getPath(), uri.getQuery(), uri.getFragment());
        }
        catch (URISyntaxException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }
}