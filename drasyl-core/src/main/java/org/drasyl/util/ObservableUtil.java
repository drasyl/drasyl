/*
 * Copyright (c) 2020-2021 Heiko Bornholdt and Kevin Röbert
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.drasyl.util;

import io.reactivex.rxjava3.core.Observable;

/**
 * Utility class for operations on {@link Observable}s.
 */
public final class ObservableUtil {
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
