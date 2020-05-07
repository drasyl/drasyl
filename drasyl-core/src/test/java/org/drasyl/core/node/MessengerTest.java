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
package org.drasyl.core.node;

import org.drasyl.core.common.messages.Message;
import org.drasyl.core.common.models.Pair;
import org.drasyl.core.models.Code;
import org.drasyl.core.models.DrasylException;
import org.drasyl.core.models.Event;
import org.drasyl.core.node.identity.Identity;
import org.drasyl.core.node.identity.IdentityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class MessengerTest {
    private IdentityManager identityManager;
    private PeersManager peersManager;
    private Consumer<Event> onEvent;
    private Identity sender;
    private Identity recipient;
    private byte[] payload;
    private PeerInformation peerInformation;
    private PeerConnection peerConnection;

    @BeforeEach
    void setUp() {
        identityManager = mock(IdentityManager.class);
        peersManager = mock(PeersManager.class);
        onEvent = mock(Consumer.class);
        sender = mock(Identity.class);
        recipient = mock(Identity.class);
        payload = new byte[]{ 0x4f };
        peerInformation = mock(PeerInformation.class);
        peerConnection = mock(PeerConnection.class);
    }

    @Test
    public void sendShouldSendTheMessageToItself() throws DrasylException {
        when(identityManager.getIdentity()).thenReturn(recipient);

        Messenger messenger = new Messenger(identityManager, peersManager, onEvent);
        messenger.send(new Message(sender, recipient, payload));

        verify(onEvent).accept(new Event(Code.MESSAGE, Pair.of(sender, payload)));
    }

    @Test
    public void sendShouldSendTheMessageToOtherRecipientLocally() throws DrasylException {
        when(identityManager.getIdentity()).thenReturn(sender);
        when(peersManager.getPeer(recipient)).thenReturn(peerInformation);
        when(peerInformation.getConnections()).thenReturn(Set.of(peerConnection));

        Message message = new Message(sender, recipient, payload);
        Messenger messenger = new Messenger(identityManager, peersManager, onEvent);
        messenger.send(message);

        verify(peerConnection).send(message);
    }

    @Test
    public void sendShouldSendTheMessageToOtherRecipientRemotely() throws DrasylException {
        when(identityManager.getIdentity()).thenReturn(sender);
        when(peersManager.getPeer(recipient)).thenReturn(null);
        when(peersManager.getSuperPeer()).thenReturn(Pair.of(mock(Identity.class), peerInformation));
        when(peerInformation.getConnections()).thenReturn(Set.of(peerConnection));

        Message message = new Message(sender, recipient, payload);
        Messenger messenger = new Messenger(identityManager, peersManager, onEvent);
        messenger.send(message);

        verify(peerConnection).send(message);
    }

    @Test
    public void sendShouldSendFailIfSuperIsNotAvailable() {
        when(identityManager.getIdentity()).thenReturn(sender);

        Messenger messenger = new Messenger(identityManager, peersManager, onEvent);
        assertThrows(DrasylException.class, () -> {
            messenger.send(new Message(sender, recipient, payload));
        });
    }
}