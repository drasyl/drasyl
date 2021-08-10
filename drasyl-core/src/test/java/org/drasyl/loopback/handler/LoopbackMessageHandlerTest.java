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
package org.drasyl.loopback.handler;

import io.netty.util.ReferenceCounted;
import org.drasyl.DrasylConfig;
import org.drasyl.channel.AddressedMessage;
import org.drasyl.channel.EmbeddedDrasylServerChannel;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.peer.PeersManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void shouldPassMessageIfRecipientIsNotLocalNode(@Mock final IdentityPublicKey recipient,
                                                    @Mock final Object message) {
        final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager, new LoopbackMessageHandler());
        try {
            pipeline.writeAndFlush(new AddressedMessage<>(message, recipient));

            final ReferenceCounted actual = pipeline.readOutbound();
            assertEquals(new AddressedMessage<>(message, recipient), actual);

            actual.release();
        }
        finally {
            pipeline.close();
        }
    }

    @Test
    void shouldBounceMessageIfRecipientIsLocalNode(@Mock final IdentityPublicKey recipient,
                                                   @Mock(answer = Answers.RETURNS_DEEP_STUBS) final Object message) {
        when(identity.getIdentityPublicKey()).thenReturn(recipient);

        final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager, new LoopbackMessageHandler());
        try {
            pipeline.writeAndFlush(new AddressedMessage<>(message, recipient));

            final ReferenceCounted actual = pipeline.readInbound();
            assertEquals(new AddressedMessage<>(message, recipient), actual);

            actual.release();
        }
        finally {
            pipeline.close();
        }
    }
}
