/*
 * Copyright (c) 2020
 *
 * This file is part of Relayserver.
 *
 * Relayserver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Relayserver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Relayserver.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.drasyl.core.common.models;

import java.io.Serializable;
import java.util.Objects;

@SuppressWarnings({ "squid:S4144" })
public class Pair<L, R> implements Serializable {
    private static final long serialVersionUID = 1L;
    private final L leftEle; // NOSONAR
    private final R rightEle; // NOSONAR

    /**
     * Creates a new pair of two objects.
     *
     * @param leftEle  left object
     * @param rightEle right object
     */
    private Pair(L leftEle, R rightEle) {
        this.leftEle = leftEle;
        this.rightEle = rightEle;
    }

    /**
     * <p>
     * Obtains a pair of two objects inferring the generic types.
     * </p>
     *
     * <p>
     * This factory allows the pair to be created using inference to obtain the
     * generic types.
     * </p>
     *
     * @param <L>   the left element type
     * @param <R>   the right element type
     * @param left  the left element, may be null
     * @param right the right element, may be null
     * @return a pair formed from the two parameters, not null
     */
    public static <F, S> Pair<F, S> of(F firstEle, S secondEle) {
        return new Pair<>(firstEle, secondEle);
    }

    /**
     * @return the left element
     */
    public L getLeft() {
        return this.leftEle;
    }

    /**
     * @return the right element
     */
    public R getRight() {
        return this.rightEle;
    }

    public L left() {
        return this.leftEle;
    }

    public R right() {
        return this.rightEle;
    }

    @Override
    public String toString() {
        return "Pair [left=" + getLeft() + ", right=" + getRight() + "]";
    }

    @Override
    public int hashCode() {
        return Objects.hash(leftEle, rightEle);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof Pair<?, ?>) {
            Pair<?, ?> p = (Pair<?, ?>) o;

            return Objects.equals(leftEle, p.leftEle) && Objects.equals(rightEle, p.rightEle);
        }

        return false;
    }
}
