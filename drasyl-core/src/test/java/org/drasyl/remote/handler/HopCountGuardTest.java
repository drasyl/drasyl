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
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.EmbeddedPipeline;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.message.AddressedEnvelope;
import org.drasyl.pipeline.message.DefaultAddressedEnvelope;
import org.drasyl.remote.protocol.IntermediateEnvelope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HopCountGuardTest {
    @Mock
    private DrasylConfig config;
    @Mock
    private Identity identity;
    @Mock
    private PeersManager peersManager;

    @Test
    void shouldPassMessagesThatHaveNotReachedTheirHopCountLimitAndIncrementHopCount(@Mock final CompressedPublicKey recipient,
                                                                                    @Mock(answer = RETURNS_DEEP_STUBS) final IntermediateEnvelope<? extends MessageLite> message) throws IOException {
        when(config.getRemoteMessageHopLimit()).thenReturn((byte) 2);
        when(message.getHopCount()).thenReturn((byte) 1);

        final HopCountGuard handler = HopCountGuard.INSTANCE;
        try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
            final TestObserver<AddressedEnvelope<Address, Object>> outboundMessages = pipeline.outboundMessagesWithRecipient().test();

            pipeline.processOutbound(recipient, message).join();

            outboundMessages.awaitCount(1)
                    .assertValueCount(1)
                    .assertValue(new DefaultAddressedEnvelope<>(null, recipient, message));
            verify(message).incrementHopCount();
        }
    }

    @Test
    void shouldDiscardMessagesThatHaveReachedTheirHopCountLimit(@Mock final CompressedPublicKey address,
                                                                @Mock(answer = RETURNS_DEEP_STUBS) final IntermediateEnvelope<? extends MessageLite> message) throws IOException {
        when(config.getRemoteMessageHopLimit()).thenReturn((byte) 1);
        when(message.getHopCount()).thenReturn((byte) 1);
        when(message.refCnt()).thenReturn(1);

        final HopCountGuard handler = HopCountGuard.INSTANCE;
        try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
            final TestObserver<Object> outboundMessages = pipeline.outboundMessages().test();

            assertThrows(CompletionException.class, pipeline.processOutbound(message.getSender(), message)::join);

            outboundMessages.assertNoValues();
        }
    }
}
