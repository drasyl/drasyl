/*
 * Copyright (c) 2020-2021.
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
package org.drasyl.remote.handler;

import com.google.protobuf.MessageLite;
import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.DrasylConfig;
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.EmbeddedPipeline;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.message.AddressedEnvelope;
import org.drasyl.pipeline.message.DefaultAddressedEnvelope;
import org.drasyl.remote.protocol.AddressedIntermediateEnvelope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyByte;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvalidProofOfWorkFilterTest {
    @Mock
    private Identity identity;
    @Mock
    private PeersManager peersManager;
    @Mock
    private DrasylConfig config;

    @Test
    void shouldDropMessagesWithInvalidProofOfWork(@Mock(answer = RETURNS_DEEP_STUBS) final AddressedIntermediateEnvelope<MessageLite> message) throws InterruptedException, IOException {
        when(message.getContent().getProofOfWork().isValid(any(), anyByte())).thenReturn(false);
        when(message.getContent().isChunk()).thenReturn(false);
        when(message.refCnt()).thenReturn(1);

        final InvalidProofOfWorkFilter handler = InvalidProofOfWorkFilter.INSTANCE;
        try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
            final TestObserver<Object> inboundMessages = pipeline.inboundMessages().test();

            pipeline.processInbound(message.getSender(), message);

            inboundMessages.await(1, SECONDS);
            inboundMessages.assertNoValues();
        }
    }

    @Test
    void shouldPassMessagesWithValidProofOfWork(@Mock(answer = RETURNS_DEEP_STUBS) final AddressedIntermediateEnvelope<MessageLite> message) throws IOException {
        when(message.getContent().getProofOfWork().isValid(any(), anyByte())).thenReturn(true);
        when(message.getContent().isChunk()).thenReturn(false);

        final InvalidProofOfWorkFilter handler = InvalidProofOfWorkFilter.INSTANCE;
        try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
            final TestObserver<AddressedEnvelope<Address, Object>> inboundMessages = pipeline.inboundMessagesWithRecipient().test();

            pipeline.processInbound(message.getSender(), message);

            inboundMessages.awaitCount(1)
                    .assertValueCount(1)
                    .assertValue(new DefaultAddressedEnvelope<>(message.getSender(), null, message));
        }
    }

    @Test
    void shouldPassChunks(@Mock(answer = RETURNS_DEEP_STUBS) final AddressedIntermediateEnvelope<MessageLite> message) throws IOException {
        when(message.getContent().isChunk()).thenThrow(IOException.class);

        final InvalidProofOfWorkFilter handler = InvalidProofOfWorkFilter.INSTANCE;
        try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
            final TestObserver<Object> inboundMessages = pipeline.inboundMessages().test();

            final CompletableFuture<Void> future = pipeline.processInbound(message.getSender(), message);

            assertThrows(Exception.class, future::join);
            inboundMessages.assertNoValues();
        }
    }
}
