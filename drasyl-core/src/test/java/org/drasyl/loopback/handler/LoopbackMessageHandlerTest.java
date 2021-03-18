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
package org.drasyl.loopback.handler;

import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.DrasylConfig;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.EmbeddedPipeline;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoopbackMessageHandlerTest {
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Identity identity;
    @Mock
    private PeersManager peersManager;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private DrasylConfig config;

    @Test
    void shouldPassMessageIfRecipientIsNotLocalNode(@Mock final CompressedPublicKey recipient,
                                                    @Mock final Object message) {
        try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, new LoopbackMessageHandler())) {
            final TestObserver<Object> outboundMessages = pipeline.outboundMessages().test();

            pipeline.processOutbound(recipient, message);

            outboundMessages.awaitCount(1)
                    .assertValueCount(1);
        }
    }

    @Test
    void shouldBounceMessageIfRecipientIsLocalNode(@Mock final CompressedPublicKey recipient,
                                                   @Mock(answer = Answers.RETURNS_DEEP_STUBS) final Object message) {
        when(identity.getPublicKey()).thenReturn(recipient);

        try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, new LoopbackMessageHandler(true))) {
            final TestObserver<Object> inboundMessages = pipeline.inboundMessages().test();

            pipeline.processOutbound(recipient, message);

            inboundMessages.awaitCount(1)
                    .assertValueCount(1);
        }
    }
}
