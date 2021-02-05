package org.drasyl.util;

import org.drasyl.annotation.NonNull;

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
     * Converts {@code s} to an {@link InetSocketAddress}.
     *
     * @param s string to convert
     * @return {@link InetSocketAddress} converted from string
     * @throws NullPointerException     if {@code s} is {@code null}
     * @throws IllegalArgumentException if {@code s} does not contain hostname and port or could not
     *                                  be converted to a valid {@link InetSocketAddress}.
     */
    @SuppressWarnings("java:S109")
    public static InetSocketAddress socketAddressFromString(@NonNull String s) {
        final String[] split = s.split(":", 2);
        if (split.length != 2) {
            throw new IllegalArgumentException("string must contain hostname and port divided by colon");
        }

        try {
            final String hostname = split[0];
            final int port = Integer.parseInt(split[1]);

            return new InetSocketAddress(hostname, port);
        }
        catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid port number format", e);
        }
    }
}
