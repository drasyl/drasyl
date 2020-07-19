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
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.message.RelayableMessage;
import org.drasyl.peer.connection.PeerChannelGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * The Messenger is responsible for handling the outgoing message flow and sending messages to the
 * recipient.
 */
public class Messenger {
    private static final Logger LOG = LoggerFactory.getLogger(Messenger.class);
    private final MessageSink loopbackSink;
    private MessageSink intraVmSink;
    private final MessageSink channelGroupSink;

    public Messenger(MessageSink loopbackSink,
                     PeersManager peersManager,
                     PeerChannelGroup channelGroup) {
        this(loopbackSink, null, message -> {
            CompressedPublicKey recipient = message.getRecipient();

            // if recipient is a grandchild, we must send message to appropriate child
            CompressedPublicKey grandchildrenPath = peersManager.getGrandchildrenRoutes().get(recipient);
            if (grandchildrenPath != null) {
                recipient = grandchildrenPath;
            }

            try {
                channelGroup.writeAndFlush(recipient, message);
            }
            catch (IllegalArgumentException e) {
                CompressedPublicKey superPeer = peersManager.getSuperPeerKey();
                if (superPeer != null && recipient != superPeer) {
                    // no direct connection, send message to super peer
                    try {
                        channelGroup.writeAndFlush(superPeer, message);
                    }
                    catch (IllegalArgumentException e2) {
                        throw new NoPathToIdentityException(recipient);
                    }
                }
                else {
                    throw new NoPathToIdentityException(recipient);
                }
            }
        });
    }

    Messenger(MessageSink loopbackSink,
              MessageSink intraVmSink,
              MessageSink channelGroupSink) {
        this.loopbackSink = requireNonNull(loopbackSink);
        this.intraVmSink = intraVmSink;
        this.channelGroupSink = channelGroupSink;
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
    public void send(RelayableMessage message) throws MessengerException {
        LOG.trace("Send Message: {}", message);

        List<MessageSink> messageSinks = Lists.newArrayList(loopbackSink, intraVmSink, channelGroupSink)
                .stream().filter(Objects::nonNull).collect(Collectors.toList());
        for (MessageSink messageSink : messageSinks) {
            try {
                messageSink.send(message);
                LOG.trace("Message was sent with Message Sink '{}'", messageSink);
                return;
            }
            catch (NoPathToIdentityException e) {
                // do nothing (continue with next MessageSink)
            }
        }

        throw new NoPathToIdentityException(message.getRecipient());
    }

    public void setIntraVmSink(MessageSink intraVmSink) {
        this.intraVmSink = intraVmSink;
    }

    public void unsetIntraVmSink() {
        this.intraVmSink = null;
    }
}
