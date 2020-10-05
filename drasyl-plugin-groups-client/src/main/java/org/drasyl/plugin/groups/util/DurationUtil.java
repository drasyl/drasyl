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
package org.drasyl.plugin.groups.util;

import java.time.Duration;

import static java.time.Duration.ofSeconds;

public class DurationUtil {
    private DurationUtil() {
        // util class
    }

    /**
     * Normalize the given {@code duration} to a minimum of 1m.
     *
     * @param duration the duration
     * @return the normalized duration
     */
    public static Duration normalize(final Duration duration) {
        return max(ofSeconds(60), duration);
    }

    /**
     * Returns the greater of two {@link Duration} values.
     *
     * @param a an argument.
     * @param b another argument.
     * @return the larger of {@code a} and {@code b}.
     * @throws NullPointerException if one or both values are {@code null}
     */
    public static Duration max(final Duration a, final Duration b) {
        if (a.compareTo(b) > 0) {
            return a;
        }
        else {
            return b;
        }
    }
}
