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

import org.drasyl.DrasylException;
import org.drasyl.MessageSink;
import org.drasyl.NoPathToIdentityException;
import org.drasyl.identity.Address;
import org.drasyl.identity.Identity;
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessengerTest {
    private MessageSink loopbackSink;
    private MessageSink intraVmSink;
    private MessageSink serverSink;
    private MessageSink superPeerSink;
    private ApplicationMessage applicationMessage;
    private Address address;
    private NoPathToIdentityException noPathToIdentityException;

    @BeforeEach
    void setUp() {
        loopbackSink = mock(MessageSink.class);
        intraVmSink = mock(MessageSink.class);
        serverSink = mock(MessageSink.class);
        superPeerSink = mock(MessageSink.class);
        applicationMessage = mock(ApplicationMessage.class);
        address = mock(Address.class);
        noPathToIdentityException = mock(NoPathToIdentityException.class);
    }

    @Test
    void sendShouldSendMessageToLoopbackSinkIfAllSinksArePresent() throws DrasylException {
        when(applicationMessage.getRecipient()).thenReturn(address);

        Messenger messenger = new Messenger(loopbackSink, intraVmSink, serverSink, superPeerSink);
        messenger.send(applicationMessage);

        verify(loopbackSink).send(Identity.of(address), applicationMessage);
        verify(intraVmSink, never()).send(any(), any());
        verify(serverSink, never()).send(any(), any());
        verify(superPeerSink, never()).send(any(), any());
    }

    @Test
    void sendShouldSendMessageToIntraVmSinkIfLoopbackSinkCanNotSendMessage() throws DrasylException {
        when(applicationMessage.getRecipient()).thenReturn(address);
        doThrow(noPathToIdentityException).when(loopbackSink).send(any(), any());

        Messenger messenger = new Messenger(loopbackSink, intraVmSink, serverSink, superPeerSink);
        messenger.send(applicationMessage);

        verify(intraVmSink).send(Identity.of(address), applicationMessage);
        verify(serverSink, never()).send(any(), any());
        verify(superPeerSink, never()).send(any(), any());
    }

    @Test
    void sendShouldSendMessageToIntraVmSinkIfLoopbackSinkIsNotPresent() throws DrasylException {
        when(applicationMessage.getRecipient()).thenReturn(address);

        Messenger messenger = new Messenger(null, intraVmSink, serverSink, superPeerSink);
        messenger.send(applicationMessage);

        verify(intraVmSink).send(Identity.of(address), applicationMessage);
        verify(serverSink, never()).send(any(), any());
        verify(superPeerSink, never()).send(any(), any());
    }

    @Test
    void sendShouldThrowExceptionIfAllSinksCanNotSendMessage() throws DrasylException {
        when(applicationMessage.getRecipient()).thenReturn(address);
        doThrow(noPathToIdentityException).when(loopbackSink).send(any(), any());
        doThrow(noPathToIdentityException).when(intraVmSink).send(any(), any());
        doThrow(noPathToIdentityException).when(serverSink).send(any(), any());
        doThrow(noPathToIdentityException).when(superPeerSink).send(any(), any());

        Messenger messenger = new Messenger(loopbackSink, intraVmSink, serverSink, superPeerSink);

        assertThrows(NoPathToIdentityException.class, () -> messenger.send(applicationMessage));
    }

    @Test
    void sendShouldThrowExceptionIfNoSinksArePresent() {
        when(applicationMessage.getRecipient()).thenReturn(address);

        Messenger messenger = new Messenger(null, null, null, null);

        assertThrows(NoPathToIdentityException.class, () -> messenger.send(applicationMessage));
    }
}
