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

/**
 * Utility class for logging-related operations.
 */
public class LoggingUtil {
    private LoggingUtil() {
        // util class
    }

    /**
     * Cleans <code>obj</code> from line breaks and returns them as \n or \r.
     *
     * @param obj the object to be cleaned
     * @return cleaned string
     */
    public static String sanitizeLogArg(final Object obj) {
        if (obj != null) {
            return obj.toString()
                    .replace("\n", "\\n")
                    .replace("\r", "\\r");
        }
        else {
            return null;
        }
    }
}
