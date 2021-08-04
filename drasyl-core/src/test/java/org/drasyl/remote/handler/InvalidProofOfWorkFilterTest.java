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
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.DefaultEmbeddedPipeline;
import org.drasyl.pipeline.EmbeddedPipeline;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.message.AddressedEnvelope;
import org.drasyl.pipeline.message.DefaultAddressedEnvelope;
import org.drasyl.remote.protocol.AcknowledgementMessage;
import org.drasyl.remote.protocol.Nonce;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import test.util.IdentityTestUtil;

import java.util.concurrent.CompletionException;

import static org.drasyl.identity.IdentityManager.POW_DIFFICULTY;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InvalidProofOfWorkFilterTest {
    @Mock
    private PeersManager peersManager;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private DrasylConfig config;
    private IdentityPublicKey senderPublicKey;
    private IdentityPublicKey recipientPublicKey;
    private Nonce correspondingId;

    @BeforeEach
    void setUp() {
        senderPublicKey = IdentityTestUtil.ID_1.getIdentityPublicKey();
        recipientPublicKey = IdentityTestUtil.ID_2.getIdentityPublicKey();
        correspondingId = Nonce.of("ea0f284eef1567c505b126671f4293924b81b4b9d20a2be7");
    }

    @Test
    void shouldDropMessagesWithInvalidProofOfWorkAddressedToMe() {
        final AcknowledgementMessage message = AcknowledgementMessage.of(1337, senderPublicKey, ProofOfWork.of(1), recipientPublicKey, correspondingId);
        final InvalidProofOfWorkFilter handler = InvalidProofOfWorkFilter.INSTANCE;
        final EmbeddedPipeline pipeline = new DefaultEmbeddedPipeline(config, IdentityTestUtil.ID_2, peersManager, handler);
        try {
            final TestObserver<Object> inboundMessages = pipeline.inboundMessages().test();

            assertThrows(CompletionException.class, pipeline.processInbound(message.getSender(), message)::join);

            inboundMessages.assertNoValues();
        }
        finally {
            pipeline.close();
        }
    }

    @Test
    void shouldPassMessagesWithValidProofOfWorkAddressedToMe() {
        final AcknowledgementMessage message = AcknowledgementMessage.of(1337, senderPublicKey, IdentityTestUtil.ID_1.getProofOfWork(), recipientPublicKey, correspondingId);
        final InvalidProofOfWorkFilter handler = InvalidProofOfWorkFilter.INSTANCE;
        final EmbeddedPipeline pipeline = new DefaultEmbeddedPipeline(config, IdentityTestUtil.ID_2, peersManager, handler);
        try {
            final TestObserver<AddressedEnvelope<Address, Object>> inboundMessages = pipeline.inboundMessagesWithSender().test();

            pipeline.processInbound(message.getSender(), message).join();

            inboundMessages.awaitCount(1)
                    .assertValueCount(1)
                    .assertValue(new DefaultAddressedEnvelope<>(message.getSender(), null, message));
        }
        finally {
            pipeline.close();
        }
    }

    @Test
    void shouldNotValidateProofOfWorkForMessagesNotAddressedToMe(@Mock final ProofOfWork proofOfWork) {
        final AcknowledgementMessage message = AcknowledgementMessage.of(1337, senderPublicKey, proofOfWork, recipientPublicKey, correspondingId);
        final InvalidProofOfWorkFilter handler = InvalidProofOfWorkFilter.INSTANCE;
        final EmbeddedPipeline pipeline = new DefaultEmbeddedPipeline(config, IdentityTestUtil.ID_3, peersManager, handler);
        try {
            pipeline.processInbound(message.getSender(), message).join();

            verify(proofOfWork, never()).isValid(message.getSender(), POW_DIFFICULTY);
        }
        finally {
            pipeline.close();
        }
    }
}
