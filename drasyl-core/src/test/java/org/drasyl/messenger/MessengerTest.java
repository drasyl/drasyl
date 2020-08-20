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

import io.reactivex.rxjava3.subjects.Subject;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;

import static java.util.concurrent.CompletableFuture.failedFuture;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessengerTest {
    @Mock(answer = RETURNS_DEEP_STUBS)
    private MessageSink loopbackSink;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private MessageSink intraVmSink;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private MessageSink channelGroupSink;
    @Mock
    private ApplicationMessage applicationMessage;
    @Mock
    private CompressedPublicKey address;
    @Mock
    private NoPathToPublicKeyException noPathToPublicKeyException;
    @Mock
    private Subject<CompressedPublicKey> peerCommunicationOccurred;

    @Nested
    class Send {
        @Test
        void shouldSendMessageToLoopbackSinkIfAllSinksArePresent() {
            Messenger messenger = new Messenger(peerCommunicationOccurred, loopbackSink, intraVmSink, channelGroupSink);
            messenger.send(applicationMessage);

            verify(loopbackSink).send(applicationMessage);
            verify(intraVmSink, never()).send(any());
            verify(channelGroupSink, never()).send(any());
        }

        @Test
        void shouldSendMessageToIntraVmSinkIfLoopbackSinkCanNotSendMessage() {
            when(loopbackSink.send(any())).thenReturn(failedFuture(noPathToPublicKeyException));

            Messenger messenger = new Messenger(peerCommunicationOccurred, loopbackSink, intraVmSink, channelGroupSink);
            messenger.send(applicationMessage);

            verify(intraVmSink).send(applicationMessage);
            verify(channelGroupSink, never()).send(any());
        }

        @Test
        void shouldThrowExceptionIfNoSinkCanNotSendMessage() {
            when(applicationMessage.getRecipient()).thenReturn(address);
            Answer<Object> failingSink = invocation -> {
                @SuppressWarnings("unchecked")
                BiConsumer<Void, Throwable> consumer = invocation.getArgument(0, BiConsumer.class);
                consumer.accept(null, new NoPathToPublicKeyException(address));
                return null;
            };
            when(loopbackSink.send(any()).whenComplete(any())).then(failingSink);
            when(intraVmSink.send(any()).whenComplete(any())).then(failingSink);
            when(channelGroupSink.send(any()).whenComplete(any())).then(failingSink);

            Messenger messenger = new Messenger(peerCommunicationOccurred, loopbackSink, intraVmSink, channelGroupSink);

            assertThrows(ExecutionException.class, () -> messenger.send(applicationMessage).get());
        }
    }
}