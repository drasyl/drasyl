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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.Objects;

/**
 * A tuple of three elements.
 * <p>
 * Inspired by: https://github.com/javatuples/javatuples/blob/master/src/main/java/org/javatuples/Triplet.java
 */
@JsonFormat(shape = JsonFormat.Shape.ARRAY)
@SuppressWarnings({ "squid:S4144", "java:S4926" })
public class Triple<A, B, C> implements Serializable {
    private static final long serialVersionUID = 5738338228780907721L;
    private final A first; // NOSONAR
    private final B second; // NOSONAR
    private final C third; // NOSONAR

    /**
     * Creates a new tuple of three elements.
     *
     * @param first  first object
     * @param second second object
     * @param third  third object
     */
    @JsonCreator
    private Triple(@JsonProperty("first") final A first,
                   @JsonProperty("second") final B second,
                   @JsonProperty("third") final C third) {
        this.first = first;
        this.second = second;
        this.third = third;
    }

    @Override
    public int hashCode() {
        return Objects.hash(first, second, third);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Triple<?, ?, ?> triple = (Triple<?, ?, ?>) o;
        return Objects.equals(first, triple.first) &&
                Objects.equals(second, triple.second) &&
                Objects.equals(third, triple.third);
    }

    @Override
    public String toString() {
        return "Triple{" +
                "first=" + first +
                ", second=" + second +
                ", third=" + third +
                '}';
    }

    /**
     * @return the first element
     */
    @JsonProperty("first")
    public A first() {
        return first;
    }

    /**
     * @return the second element
     */
    @JsonProperty("second")
    public B second() {
        return second;
    }

    /**
     * @return the third element
     */
    @JsonProperty("third")
    public C third() {
        return third;
    }

    /**
     * <p>
     * Obtains a tuple of three elements inferring the generic types.
     * </p>
     *
     * <p>
     * This factory allows the triple to be created using inference to obtain the generic types.
     * </p>
     *
     * @param <A>    the first element type
     * @param <B>    the second element type
     * @param <C>    the third element type
     * @param first  the first element, may be null
     * @param second the second element, may be null
     * @param third  the third element, may be null
     * @return a triple formed from the three parameters, not null
     */
    public static <A, B, C> Triple<A, B, C> of(final A first, final B second, final C third) {
        return new Triple<>(first, second, third);
    }
}
