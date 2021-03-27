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
package org.drasyl.util.logging;

import java.util.Objects;

/**
 * Holds the results of formatting done by {@link MessageFormatter}.
 */
final class FormattingTuple {
    private final String message;
    private final Throwable throwable;

    FormattingTuple(final String message, final Throwable throwable) {
        this.message = message;
        this.throwable = throwable;
    }

    public FormattingTuple(final String message) {
        this(message, null);
    }

    public String getMessage() {
        return message;
    }

    public Throwable getThrowable() {
        return throwable;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final FormattingTuple that = (FormattingTuple) o;
        return Objects.equals(message, that.message) && Objects.equals(throwable, that.throwable);
    }

    @Override
    public int hashCode() {
        return Objects.hash(message, throwable);
    }

    @Override
    public String toString() {
        return "FormattingTuple{" +
                "message='" + message + '\'' +
                ", throwable=" + throwable +
                '}';
    }
}
