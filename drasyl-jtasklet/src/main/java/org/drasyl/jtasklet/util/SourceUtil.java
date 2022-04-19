package org.drasyl.jtasklet.util;

public class SourceUtil {
    private SourceUtil() {
        // util class
    }

    public static String minifySource(final String source) {
        return source.replaceAll("\\s", "");
    }
}
