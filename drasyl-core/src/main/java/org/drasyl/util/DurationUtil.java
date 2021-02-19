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

import java.time.Duration;

/**
 * Utility class for operations on {@link Duration}s.
 */
public final class DurationUtil {
    private DurationUtil() {
        // util class
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

    /**
     * Returns the smaller of two {@link Duration} values.
     *
     * @param a an argument.
     * @param b another argument.
     * @return the smaller of {@code a} and {@code b}.
     * @throws NullPointerException if one or both values are {@code null}
     */
    public static Duration min(final Duration a, final Duration b) {
        if (a.compareTo(b) < 0) {
            return a;
        }
        else {
            return b;
        }
    }
}
