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
import org.drasyl.channel.InetAddressedMessage;
import org.drasyl.channel.embedded.UserEventAwareEmbeddedChannel;
import org.drasyl.handler.remote.protocol.AcknowledgementMessage;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import test.util.IdentityTestUtil;

import java.net.InetSocketAddress;

import static org.drasyl.identity.Identity.POW_DIFFICULTY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static test.util.IdentityTestUtil.ID_1;

@ExtendWith(MockitoExtension.class)
class InvalidProofOfWorkFilterTest {
    private IdentityPublicKey senderPublicKey;
    private InetSocketAddress senderAddress;
    private IdentityPublicKey recipientPublicKey;

    @BeforeEach
    void setUp() {
        senderPublicKey = ID_1.getIdentityPublicKey();
        senderAddress = new InetSocketAddress(12345);
        recipientPublicKey = IdentityTestUtil.ID_2.getIdentityPublicKey();
    }

    @Test
    void shouldDropMessagesWithInvalidProofOfWorkAddressedToMe() {
        final AcknowledgementMessage message = AcknowledgementMessage.of(1337, recipientPublicKey, senderPublicKey, ProofOfWork.of(1), System.currentTimeMillis());
        final InvalidProofOfWorkFilter handler = new InvalidProofOfWorkFilter();
        final EmbeddedChannel channel = new UserEventAwareEmbeddedChannel(recipientPublicKey, handler);
        try {
            channel.pipeline().fireChannelRead(new InetAddressedMessage<>(message, null, senderAddress));

            assertNull(channel.readInbound());
        }
        finally {
            channel.close();
        }
    }

    @Test
    void shouldPassMessagesWithValidProofOfWorkAddressedToMe() {
        final AcknowledgementMessage message = AcknowledgementMessage.of(1337, recipientPublicKey, senderPublicKey, ID_1.getProofOfWork(), System.currentTimeMillis());
        final InvalidProofOfWorkFilter handler = new InvalidProofOfWorkFilter();
        final EmbeddedChannel channel = new UserEventAwareEmbeddedChannel(recipientPublicKey, handler);
        try {
            channel.pipeline().fireChannelRead(new InetAddressedMessage<>(message, null, senderAddress));

            final ReferenceCounted actual = channel.readInbound();
            assertEquals(new InetAddressedMessage<>(message, null, senderAddress), actual);

            actual.release();
        }
        finally {
            channel.close();
        }
    }

    @Test
    void shouldNotValidateProofOfWorkForMessagesNotAddressedToMe(@Mock final ProofOfWork proofOfWork) {
        final AcknowledgementMessage message = AcknowledgementMessage.of(1337, recipientPublicKey, senderPublicKey, proofOfWork, System.currentTimeMillis());
        final InvalidProofOfWorkFilter handler = new InvalidProofOfWorkFilter();
        final EmbeddedChannel channel = new EmbeddedChannel(handler);
        try {
            channel.pipeline().fireChannelRead(new InetAddressedMessage<>(message, null, senderAddress));

            verify(proofOfWork, never()).isValid(message.getSender(), POW_DIFFICULTY);
        }
        finally {
            channel.releaseInbound();
            channel.close();
        }
    }
}
