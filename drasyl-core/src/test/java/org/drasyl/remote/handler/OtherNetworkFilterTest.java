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
import org.drasyl.remote.protocol.RemoteEnvelope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtherNetworkFilterTest {
    @Mock
    private DrasylConfig config;
    @Mock
    private Identity identity;
    @Mock
    private PeersManager peersManager;

    @Test
    void shouldDropMessagesFromOtherNetworks(@Mock(answer = RETURNS_DEEP_STUBS) final RemoteEnvelope<MessageLite> message) throws IOException {
        when(config.getNetworkId()).thenReturn(123);
        when(message.getNetworkId()).thenReturn(456);
        when(message.refCnt()).thenReturn(1);

        final OtherNetworkFilter handler = OtherNetworkFilter.INSTANCE;
        try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
            final TestObserver<Object> inboundMessages = pipeline.inboundMessages().test();

            assertThrows(CompletionException.class, pipeline.processInbound(message.getSender(), message)::join);

            inboundMessages.assertNoValues();
        }
    }

    @Test
    void shouldPassMessagesFromSameNetwork(@Mock final Address sender,
                                           @Mock(answer = RETURNS_DEEP_STUBS) final RemoteEnvelope<MessageLite> message) throws IOException {
        when(config.getNetworkId()).thenReturn(123);
        when(message.getNetworkId()).thenReturn(123);

        final OtherNetworkFilter handler = OtherNetworkFilter.INSTANCE;
        try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
            final TestObserver<AddressedEnvelope<Address, Object>> inboundMessages = pipeline.inboundMessagesWithSender().test();

            pipeline.processInbound(sender, message).join();

            inboundMessages.awaitCount(1)
                    .assertValueCount(1)
                    .assertValue(new DefaultAddressedEnvelope<>(sender, null, message));
        }
    }
}
