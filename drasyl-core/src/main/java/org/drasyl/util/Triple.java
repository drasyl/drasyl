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
@SuppressWarnings({ "squid:S4144" })
public class Triple<A, B, C> implements Serializable {
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
