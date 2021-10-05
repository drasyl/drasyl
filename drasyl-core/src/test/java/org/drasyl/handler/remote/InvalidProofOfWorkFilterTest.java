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
package org.drasyl.handler.remote;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCounted;
import org.drasyl.channel.AddressedMessage;
import org.drasyl.handler.remote.protocol.AcknowledgementMessage;
import org.drasyl.handler.remote.protocol.Nonce;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import test.util.IdentityTestUtil;

import static org.drasyl.identity.Identity.POW_DIFFICULTY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InvalidProofOfWorkFilterTest {
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
        final AcknowledgementMessage message = AcknowledgementMessage.of(1337, recipientPublicKey, senderPublicKey, ProofOfWork.of(1), correspondingId, System.currentTimeMillis());
        final InvalidProofOfWorkFilter handler = new InvalidProofOfWorkFilter(IdentityTestUtil.ID_2.getAddress());
        final EmbeddedChannel channel = new EmbeddedChannel(handler);
        try {
            channel.pipeline().fireChannelRead(new AddressedMessage<>(message, message.getSender()));

            assertNull(channel.readInbound());
        }
        finally {
            channel.close();
        }
    }

    @Test
    void shouldPassMessagesWithValidProofOfWorkAddressedToMe() {
        final AcknowledgementMessage message = AcknowledgementMessage.of(1337, recipientPublicKey, senderPublicKey, IdentityTestUtil.ID_1.getProofOfWork(), correspondingId, System.currentTimeMillis());
        final InvalidProofOfWorkFilter handler = new InvalidProofOfWorkFilter(IdentityTestUtil.ID_2.getAddress());
        final EmbeddedChannel channel = new EmbeddedChannel(handler);
        try {
            channel.pipeline().fireChannelRead(new AddressedMessage<>(message, message.getSender()));

            final ReferenceCounted actual = channel.readInbound();
            assertEquals(new AddressedMessage<>(message, message.getSender()), actual);

            actual.release();
        }
        finally {
            channel.close();
        }
    }

    @Test
    void shouldNotValidateProofOfWorkForMessagesNotAddressedToMe(@Mock final ProofOfWork proofOfWork) {
        final AcknowledgementMessage message = AcknowledgementMessage.of(1337, recipientPublicKey, senderPublicKey, proofOfWork, correspondingId, System.currentTimeMillis());
        final InvalidProofOfWorkFilter handler = new InvalidProofOfWorkFilter(IdentityTestUtil.ID_3.getAddress());
        final EmbeddedChannel channel = new EmbeddedChannel(handler);
        try {
            channel.pipeline().fireChannelRead(new AddressedMessage<>(message, message.getSender()));

            verify(proofOfWork, never()).isValid(message.getSender(), POW_DIFFICULTY);
        }
        finally {
            channel.releaseInbound();
            channel.close();
        }
    }
}
