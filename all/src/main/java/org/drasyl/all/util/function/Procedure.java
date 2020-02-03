/*
 * Copyright (c) 2020
 *
 * This file is part of drasyl.
 *
 * drasyl is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * drasyl is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.drasyl.all.util.function;

/**
 * Represents an operation that accepts no input arguments and returns no
 * result.
 *
 * <p>
 * This is a <a href=
 * "https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/util/function/package-summary.html">functional
 * interface</a> whose functional method is {@link #execute()}.
 * </p>
 */
@FunctionalInterface
public interface Procedure {
    /**
     * Performs this operation.
     */
    void execute();
}
