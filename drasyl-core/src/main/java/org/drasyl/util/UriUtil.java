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

import com.google.common.base.Splitter;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

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
     * <p> This convenience factory method works as if by invoking the {@link URI#URI(String,
     * String, String, int, String, String, String)} constructor; any {@link URISyntaxException}
     * thrown by the constructor is caught and wrapped in a new {@link IllegalArgumentException}
     * object, which is then thrown.
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
     *                                  RFC&nbsp;2396
     */
    public static URI createUri(final String scheme, final String host, final int port) {
        try {
            return new URI(scheme, null, host, port, null, null, null);
        }
        catch (final URISyntaxException x) {
            throw new IllegalArgumentException(x.getMessage(), x);
        }
    }

    /**
     * Creates a URI by parsing the given components.
     *
     * <p> This convenience factory method works as if by invoking the {@link URI#URI(String,
     * String, String, int, String, String, String)} constructor; any {@link URISyntaxException}
     * thrown by the constructor is caught and wrapped in a new {@link IllegalArgumentException}
     * object, which is then thrown.
     *
     * <p> This method is provided for use in situations where it is known that
     * the given components results in a legal URI, for example for URI constants declared within a
     * program, and so it would be considered a programming error for the given components not to
     * parse as such. The constructors, which throw {@link URISyntaxException} directly, should be
     * used in situations where a URI is being constructed from user input or from some other source
     * that may be prone to errors.  </p>
     *
     * @param scheme   Scheme name The scheme to be parsed into a URL
     * @param userInfo User name and authorization information
     * @param host     Host name The host to be parsed into a URL
     * @param port     Port number The port to be parsed into a URL
     * @param path     Path
     * @param query    Query
     * @return The new URI
     * @throws IllegalArgumentException If the URI constructed from the given components violates
     *                                  RFC&nbsp;2396
     */
    public static URI createUri(final String scheme,
                                final String userInfo,
                                final String host,
                                final int port,
                                final String path,
                                final String query) {
        try {
            return new URI(scheme, userInfo, host, port, path, query, null);
        }
        catch (final URISyntaxException x) {
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
    public static URI overridePort(final URI uri, final int port) {
        try {
            return new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), port, uri.getPath(), uri.getQuery(), uri.getFragment());
        }
        catch (final URISyntaxException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    /**
     * This method sets the fragment in {@code uri} to {@code fragment} and returns the resulting
     * URI.
     *
     * @param uri      the base URI
     * @param fragment the fragment
     * @return a combined URI and fragment URI
     * @throws IllegalArgumentException if resulting URI violates RFC&nbsp;2396
     */
    public static URI overrideFragment(final URI uri, final String fragment) {
        try {
            return new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(), uri.getPath(), uri.getQuery(), fragment);
        }
        catch (final URISyntaxException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    /**
     * This method removes the fragment in {@code uri} and returns the resulting URI.
     *
     * @param uri the base URI
     * @return URI without fragment
     * @throws IllegalArgumentException if resulting URI violates RFC&nbsp;2396
     */
    public static URI removeFragment(final URI uri) {
        if (uri.getFragment() != null) {
            try {
                return new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(), uri.getPath(), uri.getQuery(), null);
            }
            catch (final URISyntaxException e) {
                throw new IllegalArgumentException(e.getMessage(), e);
            }
        }
        else {
            return uri;
        }
    }

    /**
     * Returns the query parameters of the given {@code uri} or an empty map if the query is {@code
     * null}.
     *
     * @param uri the URI
     * @return query parameters as map
     * @see <a href="https://stackoverflow.com/a/11733697">https://stackoverflow.com/a/11733697</a>
     */
    public static Map<String, String> getQueryMap(final URI uri) {
        final String query = Optional.ofNullable(uri.getQuery()).orElse("");
        final Map<String, String> map = new HashMap<>();

        for (final String param : Splitter.on("&").omitEmptyStrings().split(query)) {
            final String name = param.split("=")[0];
            final String value = param.split("=")[1];
            map.put(name, value);
        }

        return map;
    }
}