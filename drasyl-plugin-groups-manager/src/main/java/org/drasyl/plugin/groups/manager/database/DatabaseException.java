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
package org.drasyl.plugin.groups.manager.database;

import org.drasyl.DrasylException;

/**
 * This exception signals an error occurred during execution in {@link DatabaseAdapter}
 * implementations.
 */
public class DatabaseException extends DrasylException {
    /**
     * @param message the detail message. The detail message is saved for later retrieval by the
     *                {@link #getMessage()} method.
     */
    public DatabaseException(final String message) {
        super(message);
    }

    /**
     * @param message the detail message (which is saved for later retrieval by the {@link
     *                #getMessage()} method).
     * @param cause   the cause (which is saved for later retrieval by the {@link #getCause()}
     *                method).  (A {@code null} value is permitted, and indicates that the cause is
     *                nonexistent or unknown.)
     */
    public DatabaseException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
