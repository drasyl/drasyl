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

import io.netty.channel.ChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCounted;
import org.drasyl.channel.InetAddressedMessage;
import org.drasyl.handler.remote.protocol.AcknowledgementMessage;
import org.drasyl.handler.remote.protocol.RemoteMessage;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtherNetworkFilterTest {
    private IdentityPublicKey senderPublicKey;
    private IdentityPublicKey recipientPublicKey;

    @BeforeEach
    void setUp() {
        senderPublicKey = IdentityPublicKey.of("18cdb282be8d1293f5040cd620a91aca86a475682e4ddc397deabe300aad9127");
        recipientPublicKey = IdentityPublicKey.of("02bfa672181ef9c0a359dc68cc3a4d34f47752c8886a0c5661dc253ff5949f1b");
    }

    @Test
    void shouldDropMessagesFromOtherNetworks(@Mock final RemoteMessage msg,
                                             @Mock final InetSocketAddress address) {
        when(msg.getNetworkId()).thenReturn(1337);
        final ChannelHandler handler = new OtherNetworkFilter(123);
        final EmbeddedChannel channel = new EmbeddedChannel(handler);
        try {
            channel.pipeline().fireChannelRead(new InetAddressedMessage<>(msg, address));

            assertNull(channel.readInbound());
        }
        finally {
            channel.close();
        }
    }

    @Test
    void shouldPassMessagesFromSameNetwork(@Mock final InetSocketAddress sender) {
        final ChannelHandler handler = new OtherNetworkFilter(123);
        final AcknowledgementMessage message = AcknowledgementMessage.of(123, recipientPublicKey, senderPublicKey, ProofOfWork.of(1), System.currentTimeMillis());
        final EmbeddedChannel channel = new EmbeddedChannel(handler);
        try {
            channel.pipeline().fireChannelRead(new InetAddressedMessage<>(message, sender));

            final ReferenceCounted actual = channel.readInbound();
            assertEquals(new InetAddressedMessage<>(message, sender), actual);

            actual.release();
        }
        finally {
            channel.close();
        }
    }
}
