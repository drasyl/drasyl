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
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessengerTest {
    @Mock
    private MessageSink loopbackSink;
    @Mock
    private MessageSink intraVmSink;
    @Mock
    private MultiMessageSink directConnectionSink;
    @Mock
    private MessageSink serverSink;
    @Mock
    private MessageSink superPeerSink;
    @Mock
    private ApplicationMessage applicationMessage;
    @Mock
    private CompressedPublicKey address;
    @Mock
    private NoPathToIdentityException noPathToIdentityException;

    @Nested
    class Send {
        @Test
        void shouldSendMessageToLoopbackSinkIfAllSinksArePresent() throws DrasylException {
            Messenger messenger = new Messenger(loopbackSink, intraVmSink, directConnectionSink, serverSink, superPeerSink);
            messenger.send(applicationMessage);

            verify(loopbackSink).send(applicationMessage);
            verify(intraVmSink, never()).send(any());
            verify(serverSink, never()).send(any());
            verify(superPeerSink, never()).send(any());
        }

        @Test
        void shouldSendMessageToIntraVmSinkIfLoopbackSinkCanNotSendMessage() throws DrasylException {
            doThrow(noPathToIdentityException).when(loopbackSink).send(any());

            Messenger messenger = new Messenger(loopbackSink, intraVmSink, directConnectionSink, serverSink, superPeerSink);
            messenger.send(applicationMessage);

            verify(intraVmSink).send(applicationMessage);
            verify(serverSink, never()).send(any());
            verify(superPeerSink, never()).send(any());
        }

        @Test
        void shouldSendMessageToDirectConnectionsSinkIfIntraVmSinkIsNotPresent() throws DrasylException {
            doThrow(NoPathToIdentityException.class).when(loopbackSink).send(any());

            Messenger messenger = new Messenger(loopbackSink, null, directConnectionSink, serverSink, superPeerSink);
            messenger.send(applicationMessage);

            verify(directConnectionSink).send(applicationMessage);
            verify(serverSink, never()).send(any());
            verify(superPeerSink, never()).send(any());
        }

        @Test
        void shouldThrowExceptionIfAllSinksCanNotSendMessage() throws DrasylException {
            when(applicationMessage.getRecipient()).thenReturn(address);
            doThrow(noPathToIdentityException).when(loopbackSink).send(any());
            doThrow(noPathToIdentityException).when(intraVmSink).send(any());
            doThrow(noPathToIdentityException).when(directConnectionSink).send(any());
            doThrow(noPathToIdentityException).when(serverSink).send(any());
            doThrow(noPathToIdentityException).when(superPeerSink).send(any());

            Messenger messenger = new Messenger(loopbackSink, intraVmSink, directConnectionSink, serverSink, superPeerSink);

            assertThrows(NoPathToIdentityException.class, () -> messenger.send(applicationMessage));
        }

        @Test
        void shouldThrowExceptionIfNoSinkCanProcessMessage() throws MessageSinkException {
            when(applicationMessage.getRecipient()).thenReturn(address);
            doThrow(NoPathToIdentityException.class).when(loopbackSink).send(any());
            doThrow(NoPathToIdentityException.class).when(directConnectionSink).send(any());

            Messenger messenger = new Messenger(loopbackSink, null, directConnectionSink, null, null);

            assertThrows(NoPathToIdentityException.class, () -> messenger.send(applicationMessage));
        }
    }
}
