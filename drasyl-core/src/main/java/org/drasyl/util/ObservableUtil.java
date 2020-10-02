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

import io.reactivex.rxjava3.core.Observable;

/**
 * Utility class for operations on {@link Observable}s.
 */
public class ObservableUtil {
    private ObservableUtil() {
        // util class
    }

    /**
     * Creates an {@link Observable} to pair up the current and previous items.
     * <pre>
     * Source:
     *   +---+  +---+  +---+
     * --+ A +--+ B +--+ C +-&gt;
     *   +---+  +---+  +---+
     *
     * pairWithPreviousObservable:
     *   +---+  +---+  +---+
     * --+ A +--+ B +--+ C +-&gt;
     *   | ⊥ |  | A |  | B |
     *   +---+  +---+  +---+
     * </pre>
     * Adapted from http://www.zerobugbuild.com/?p=213
     *
     * @param observable {@code Observable} whose current and previous items are to be paired.
     * @param <T>        the common element type
     * @return the new {@code Observable} instance
     */
    public static <T> Observable<Pair<T, T>> pairWithPreviousObservable(final Observable<T> observable) {
        return observable.scan(Pair.of(null, null), (Pair<T, T> accumulator, T current) -> Pair.of(current, accumulator.first())).skip(1);
    }
}