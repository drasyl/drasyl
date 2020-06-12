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
package org.drasyl.messenger;

import org.drasyl.identity.Identity;
import org.drasyl.peer.connection.message.Message;

/**
 * Implement this interface and add the object to the {@link org.drasyl.messenger.Messenger} to
 * provide new communication paths for the node
 */
public interface MessageSink {
    /**
     * Sends <code>message</code> to <code>recipient</code>.
     *
     * @param recipient recipient of the message
     * @param message   message to be sent
     * @throws MessageSinkException if sending is not possible (e.g. because no path to the peer
     *                              exists)
     */
    void send(Identity recipient, Message message) throws MessageSinkException;
}
