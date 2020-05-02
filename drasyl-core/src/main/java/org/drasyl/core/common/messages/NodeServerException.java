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
package org.drasyl.core.common.messages;

import java.util.Objects;

/**
 * A message representing an exception. Such an exception should always be handled.
 */
@SuppressWarnings({ "squid:S2166" })
public class NodeServerException extends AbstractMessage implements UnrestrictedPassableMessage {
    private final String exception;

    protected NodeServerException() {
        exception = null;
    }

    /**
     * Creates a new exception message.
     *
     * @param exception the exception as String
     */
    public NodeServerException(String exception) {
        this.exception = Objects.requireNonNull(exception);
    }

    /**
     * Creates a new exception message.
     *
     * @param exception the exception
     */
    public NodeServerException(Exception exception) {
        this(exception.getMessage());
    }

    /**
     * Creates a new exception message.
     *
     * @param exception the exception
     */
    public NodeServerException(Throwable exception) {
        this(exception.getMessage());
    }

    /**
     * @return the exception
     */
    public String getException() {
        return exception;
    }

    @Override
    public String toString() {
        return "NodeServerException [exception=" + exception + ", messageID=" + getMessageID() + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof NodeServerException)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        NodeServerException that = (NodeServerException) o;
        return Objects.equals(getException(), that.getException());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), getException());
    }
}
