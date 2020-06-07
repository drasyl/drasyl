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

import com.google.common.collect.Lists;
import org.drasyl.MessageSink;
import org.drasyl.NoPathToIdentityException;
import org.drasyl.identity.Address;
import org.drasyl.identity.Identity;
import org.drasyl.peer.connection.message.ApplicationMessage;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * The Messenger is responsible for handling the outgoing message flow and sending messages to the
 * recipient.
 */
public class Messenger {
    private MessageSink loopbackSink;
    private MessageSink serverSink;
    private MessageSink superPeerSink;

    public Messenger() {
        this(null, null, null);
    }

    Messenger(MessageSink loopbackSink, MessageSink serverSink, MessageSink superPeerSink) {
        this.loopbackSink = loopbackSink;
        this.serverSink = serverSink;
        this.superPeerSink = superPeerSink;
    }

    /**
     * Sends <code>message</code> to the recipient defined in the message. Throws a {@link
     * MessengerException} if sending is not possible (e.g. because no path to the peer exists).
     * <p>
     * The method tries to choose the best path to send the message to the recipient. Thus, it first
     * checks whether the recipient can be reached locally. Then it searches for direct connections
     * to the recipient. If there is no direct connection, the message is relayed to the Super
     * Peer.
     *
     * @param message message to be sent
     * @throws MessengerException if sending is not possible (e.g. because no path to the peer
     *                            exists)
     */
    public void send(ApplicationMessage message) throws MessengerException {
        Address recipientAddress = message.getRecipient();
        Identity identity = Identity.of(recipientAddress);

        List<MessageSink> messageSinks = Lists.newArrayList(loopbackSink, serverSink, superPeerSink)
                .stream().filter(Objects::nonNull).collect(Collectors.toList());
        for (MessageSink messageSink : messageSinks) {
            try {
                messageSink.send(identity, message);
                return;
            }
            catch (NoPathToIdentityException e) {
                // do nothing (continue with next MessageSink)
            }
        }

        throw new NoPathToIdentityException(identity);
    }

    public void setLoopbackSink(MessageSink loopbackSink) {
        this.loopbackSink = loopbackSink;
    }

    public void unsetLoopbackSink() {
        this.loopbackSink = null;
    }

    public void setServerSink(MessageSink serverSink) {
        this.serverSink = serverSink;
    }

    public void unsetServerSink() {
        this.serverSink = null;
    }

    public void setSuperPeerSink(MessageSink superPeerSink) {
        this.superPeerSink = superPeerSink;
    }

    public void unsetRelaySink() {
        this.superPeerSink = null;
    }
}
