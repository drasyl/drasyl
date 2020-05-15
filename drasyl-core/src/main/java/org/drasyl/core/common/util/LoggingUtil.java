package org.drasyl.core.common.util;

public class LoggingUtil {
    private LoggingUtil() {
        // util class
    }

    /**
     * Cleans <code>obj</code> from line breaks and returns them as \n or \r.
     *
     * @param obj
     * @return
     */
    public static String sanitizeLogArg(Object obj) {
        if (obj != null) {
            return obj.toString()
                    .replaceAll("\n", "\\\\n")
                    .replaceAll("\r", "\\\\r");
        }
        else {
            return null;
        }
    }
}
