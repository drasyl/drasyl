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
package org.drasyl.core.common.message;

import org.drasyl.core.common.message.action.MessageAction;
import org.drasyl.core.common.message.action.MessageExceptionMessageAction;

import java.util.Objects;

/**
 * A message representing an exception that refers to a message. Such an exception should always be
 * handled.
 */
@SuppressWarnings({ "squid:S2166" })
public class MessageExceptionMessage extends AbstractResponseMessage<RequestMessage<?>, MessageExceptionMessage> implements UnrestrictedPassableMessage {
    private final String exception;

    protected MessageExceptionMessage() {
        super(null);
        exception = null;
    }

    /**
     * Creates a new exception message.
     *
     * @param exception the exception
     * @param correspondingId
     */
    public MessageExceptionMessage(Exception exception, String correspondingId) {
        this(exception.getMessage(), correspondingId);
    }

    /**
     * Creates a new exception message.
     *
     * @param exception the exception as String
     * @param correspondingId
     */
    public MessageExceptionMessage(String exception, String correspondingId) {
        super(correspondingId);
        this.exception = Objects.requireNonNull(exception);
    }

    /**
     * Creates a new exception message.
     *
     * @param exception the exception
     * @param correspondingId
     */
    public MessageExceptionMessage(Throwable exception, String correspondingId) {
        this(exception.getMessage(), correspondingId);
    }

    /**
     * @return the exception
     */
    public String getException() {
        return exception;
    }

    @Override
    public MessageAction<MessageExceptionMessage> getAction() {
        return new MessageExceptionMessageAction(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        MessageExceptionMessage that = (MessageExceptionMessage) o;
        return Objects.equals(exception, that.exception);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), exception);
    }

    @Override
    public String toString() {
        return "MessageExceptionMessage{" +
                "exception='" + exception + '\'' +
                ", correspondingId='" + correspondingId + '\'' +
                ", id='" + id + '\'' +
                ", signature=" + signature +
                '}';
    }
}
