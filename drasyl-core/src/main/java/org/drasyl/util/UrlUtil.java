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

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Utility class for operations on {@link URL}s.
 */
public class UrlUtil {
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