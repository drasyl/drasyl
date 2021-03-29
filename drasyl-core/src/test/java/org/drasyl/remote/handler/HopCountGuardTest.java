/*
 * Copyright (c) 2020-2021 Heiko Bornholdt and Kevin Röbert
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.drasyl.remote.handler;

import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.DrasylConfig;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.EmbeddedPipeline;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.message.AddressedEnvelope;
import org.drasyl.pipeline.message.DefaultAddressedEnvelope;
import org.drasyl.remote.protocol.MessageId;
import org.drasyl.remote.protocol.Protocol;
import org.drasyl.remote.protocol.RemoteEnvelope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HopCountGuardTest {
    @Mock
    private DrasylConfig config;
    @Mock
    private Identity identity;
    @Mock
    private PeersManager peersManager;
    private CompressedPublicKey senderPublicKey;
    private CompressedPublicKey recipientPublicKey;
    private MessageId correspondingId;

    @BeforeEach
    void setUp() {
        senderPublicKey = CompressedPublicKey.of("0229041b273dd5ee1c2bef2d77ae17dbd00d2f0a2e939e22d42ef1c4bf05147ea9");
        recipientPublicKey = CompressedPublicKey.of("030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3");
        correspondingId = MessageId.of("412176952b5b81fd");
    }

    @Test
    void shouldPassMessagesThatHaveNotReachedTheirHopCountLimitAndIncrementHopCount(@Mock final CompressedPublicKey recipient) throws IOException {
        when(config.getRemoteMessageHopLimit()).thenReturn((byte) 2);

        final HopCountGuard handler = HopCountGuard.INSTANCE;
        try (final RemoteEnvelope<Protocol.Acknowledgement> message = RemoteEnvelope.acknowledgement(1337, senderPublicKey, ProofOfWork.of(1), recipientPublicKey, correspondingId)) {
            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                final TestObserver<AddressedEnvelope<Address, Object>> outboundMessages = pipeline.outboundMessagesWithRecipient().test();

                pipeline.processOutbound(recipient, message).join();

                outboundMessages.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(new DefaultAddressedEnvelope<>(null, recipient, message));

                assertEquals((byte) 1, message.getHopCount());
            }
        }
    }

    @Test
    void shouldDiscardMessagesThatHaveReachedTheirHopCountLimit() throws IOException {
        when(config.getRemoteMessageHopLimit()).thenReturn((byte) 1);

        final HopCountGuard handler = HopCountGuard.INSTANCE;
        try (final RemoteEnvelope<Protocol.Acknowledgement> message = RemoteEnvelope.acknowledgement(1337, senderPublicKey, ProofOfWork.of(1), recipientPublicKey, correspondingId)) {
            message.incrementHopCount();
            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                final TestObserver<Object> outboundMessages = pipeline.outboundMessages().test();

                assertThrows(CompletionException.class, pipeline.processOutbound(message.getSender(), message)::join);

                outboundMessages.assertNoValues();
            }
        }
    }
}
