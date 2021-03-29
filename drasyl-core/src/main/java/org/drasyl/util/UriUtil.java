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

import com.google.common.base.Splitter;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Utility class for operations on {@link URI}s.
 */
public final class UriUtil {
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
     * Returns the query parameters of the given {@code uri} or an empty map if the query is {@code
     * null}.
     *
     * @param uri the URI
     * @return query parameters as map
     * @throws NullPointerException if {@code uri} is {@code null}
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
