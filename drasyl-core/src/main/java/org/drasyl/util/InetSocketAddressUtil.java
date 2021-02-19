/*
 * Copyright (c) 2020-2021.
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

import org.drasyl.annotation.NonNull;
import org.drasyl.pipeline.address.InetSocketAddressWrapper;

import java.net.InetSocketAddress;
import java.net.URL;

/**
 * Utility class for operations on {@link URL}s.
 */
public final class InetSocketAddressUtil {
    private InetSocketAddressUtil() {
        // util class
    }

    /**
     * Converts {@code s} to an {@link InetSocketAddressWrapper}.
     *
     * @param s string to convert
     * @return {@link InetSocketAddressWrapper} converted from string
     * @throws NullPointerException     if {@code s} is {@code null}
     * @throws IllegalArgumentException if {@code s} does not contain hostname and port or could not
     *                                  be converted to a valid {@link InetSocketAddress}.
     */
    @SuppressWarnings("java:S109")
    public static InetSocketAddressWrapper socketAddressFromString(@NonNull final String s) {
        final String[] split = s.split(":", 2);
        if (split.length != 2) {
            throw new IllegalArgumentException("string must contain hostname and port divided by colon");
        }

        try {
            final String hostname = split[0];
            final int port = Integer.parseInt(split[1]);

            return new InetSocketAddressWrapper(hostname, port);
        }
        catch (final NumberFormatException e) {
            throw new IllegalArgumentException("Invalid port number format", e);
        }
    }
}
