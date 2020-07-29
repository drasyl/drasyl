package org.drasyl.util;

import java.net.MalformedURLException;
import java.net.URL;

import static java.util.Objects.requireNonNull;

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
     * URL(String)} constructor; any {@link MalformedURLException} thrown by the constructor is
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
     * @throws NullPointerException     If {@code str} is {@code null}
     * @throws IllegalArgumentException If the given string violates RFC&nbsp;2396
     */
    public static URL createUrl(String str) {
        try {
            return new URL(requireNonNull(str));
        }
        catch (MalformedURLException x) {
            throw new IllegalArgumentException(x.getMessage(), x);
        }
    }
}
