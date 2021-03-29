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

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Utility class for operations on {@link URL}s.
 */
public final class UrlUtil {
    private UrlUtil() {
        // util class
    }

    /**
     * Creates a {@link URL} by parsing the given string.
     *
     * <p> This convenience factory method works as if by invoking the {@link
     * URL#URL(String)} constructor; any {@link MalformedURLException} thrown by the constructor is
     * caught and wrapped in a new {@link IllegalArgumentException} object, which is then thrown.
     *
     * <p> This method is provided for use in situations where it is known that
     * the given string is a legal URL, for example for URL constants declared within a program, and
     * so it would be considered a programming error for the string not to parse as such. The
     * constructors, which throw {@link MalformedURLException} directly, should be used in
     * situations where a URL is being constructed from user input or from some other source that
     * may be prone to errors.  </p>
     *
     * @param str The string to be parsed into a URL
     * @return The new URL
     * @throws IllegalArgumentException if no protocol is specified, or an unknown protocol is
     *                                  found, or {@code spec} is {@code null}, or the parsed URL
     *                                  fails to comply with the specific syntax of the associated
     *                                  protocol.
     */
    public static URL createUrl(final String str) {
        try {
            return new URL(str);
        }
        catch (final MalformedURLException x) {
            throw new IllegalArgumentException(x.getMessage(), x);
        }
    }
}
