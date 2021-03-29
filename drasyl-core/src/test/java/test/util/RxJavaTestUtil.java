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
package test.util;

import io.reactivex.rxjava3.observers.BaseTestConsumer;
import org.drasyl.annotation.NonNull;

import java.util.Arrays;
import java.util.List;

@SuppressWarnings("unused")
public final class RxJavaTestUtil {
    private RxJavaTestUtil() {
        // util class
    }

    /**
     * Assert that the {@code TestObserver}/{@code TestSubscriber} received only the specified
     * values in any order.
     *
     * @param values the values expected
     * @return {@code baseTestConsumer}
     */
    @SafeVarargs
    @NonNull
    public static <T, U extends BaseTestConsumer<T, U>> U assertValues(@NonNull final U baseTestConsumer,
                                                                       @NonNull final T... values) {
        final List<T> actualValues = baseTestConsumer.values();
        final int s = actualValues.size();

        if (s != values.length) {
            throw new AssertionError("Value count differs; expected: " + values.length
                    + " " + Arrays.toString(values) + " but was: " + s + " " + actualValues);
        }

        for (final T value : values) {
            if (!actualValues.contains(value)) {
                throw new AssertionError("expected: " + U.valueAndClass(value)
                        + " but was not included");
            }
        }

        return baseTestConsumer;
    }
}
