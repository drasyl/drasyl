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
package org.drasyl;

import java.util.Objects;

/**
 * All checked exceptions in drasyl inherit from this exception class.
 */
public class DrasylException extends Exception {
    /**
     * @param cause the cause (which is saved for later retrieval by the {@link #getCause()}
     *              method).  (A {@code null} value is permitted, and indicates that the cause is
     *              nonexistent or unknown.)
     */
    public DrasylException(final Throwable cause) {
        super(cause);
    }

    /**
     * @param message the detail message. The detail message is saved for later retrieval by the
     *                {@link #getMessage()} method.
     */
    public DrasylException(final String message) {
        super(message);
    }

    /**
     * @param message the detail message (which is saved for later retrieval by the {@link
     *                #getMessage()} method).
     * @param cause   the cause (which is saved for later retrieval by the {@link #getCause()}
     *                method).  (A {@code null} value is permitted, and indicates that the cause is
     *                nonexistent or unknown.)
     */
    public DrasylException(final String message, final Throwable cause) {
        super(message, cause);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getCause(), getMessage());
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DrasylException that = (DrasylException) o;
        return Objects.equals(getCause(), that.getCause()) &&
                Objects.equals(getMessage(), that.getMessage());
    }
}