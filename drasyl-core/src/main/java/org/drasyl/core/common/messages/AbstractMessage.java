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

import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.Signature;

import java.util.Objects;

/**
 * Message that represents a message from one node to another one.
 */
public abstract class AbstractMessage implements IMessage {
    private final String messageID;
    private Signature signature;

    protected AbstractMessage(String messageID) {
        Objects.requireNonNull(messageID);

        this.messageID = messageID;
    }

    public AbstractMessage() {
        this(Crypto.randomString(12));
    }

    @Override
    public void setSignature(Signature signature) {
        this.signature = signature;
    }

    public Signature getSignature() {
        return signature;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [messageID=" + getMessageID() + ", signature=" + signature + "]";
    }

    @Override
    public String getMessageID() {
        return messageID;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof AbstractMessage;
    }

    @Override
    public int hashCode() {
        return 42;
    }
}
