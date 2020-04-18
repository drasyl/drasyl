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

package city.sane.relay.common.messages;

import city.sane.relay.common.util.random.RandomUtil;

/**
 * Message that represents a message to the relay server or that should be
 * forwarded.
 */
public abstract class AbstractMessage implements Message {
    private final String messageID;

    protected AbstractMessage(String messageID) {
        this.messageID = messageID;
    }

    public AbstractMessage() {
        this(RandomUtil.randomString(12));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [messageID=" + getMessageID() + "]";
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
