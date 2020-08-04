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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.Objects;

/**
 * A tuple of two elements.
 * <p>
 * Inspired by: https://github.com/javatuples/javatuples/blob/master/src/main/java/org/javatuples/Pair.java
 */
@JsonFormat(shape = JsonFormat.Shape.ARRAY)
@SuppressWarnings({ "squid:S4144" })
public class Pair<A, B> implements Serializable {
    private final A first; // NOSONAR
    private final B second; // NOSONAR

    /**
     * Creates a new tuple of two elements.
     *
     * @param first  first object
     * @param second second object
     */
    @JsonCreator
    private Pair(@JsonProperty("first") A first,
                 @JsonProperty("second") B second) {
        this.first = first;
        this.second = second;
    }

    @Override
    public int hashCode() {
        return Objects.hash(first, second);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Pair<?, ?> pair = (Pair<?, ?>) o;

        return Objects.deepEquals(first, pair.first) &&
                Objects.deepEquals(second, pair.second);
    }

    @Override
    public String toString() {
        return "Pair{" +
                "first=" + first +
                ", second=" + second +
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
     * <p>
     * Obtains a tuple of two elements inferring the generic types.
     * </p>
     *
     * <p>
     * This factory allows the pair to be created using inference to obtain the generic types.
     * </p>
     *
     * @param <A>    the first element type
     * @param <B>    the second element type
     * @param first  the first element, may be null
     * @param second the second element, may be null
     * @return a pair formed from the two parameters, not null
     */
    public static <A, B> Pair<A, B> of(A first, B second) {
        return new Pair<>(first, second);
    }
}