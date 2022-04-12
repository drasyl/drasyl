/*
 * Copyright (c) 2020-2022 Heiko Bornholdt and Kevin Röbert
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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import org.drasyl.channel.InetAddressedMessage;
import org.drasyl.channel.OverlayAddressedMessage;
import org.drasyl.identity.DrasylAddress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnresolvedOverlayMessageHandlerTest {
    @Test
    void shouldFailAndDropUnresolvedMessages(@Mock final DrasylAddress address) {
        final ByteBuf buf = Unpooled.buffer();
        final OverlayAddressedMessage<ByteBuf> msg = new OverlayAddressedMessage<>(buf, address);

        final EmbeddedChannel channel = new EmbeddedChannel(new UnresolvedOverlayMessageHandler());

        final ChannelPromise promise = channel.newPromise();
        assertThrows(Exception.class, () -> channel.writeOutbound(msg, promise));
        assertEquals(0, buf.refCnt());

        channel.close();
    }

    @Test
    void shouldPassThroughResolvedMessages(@Mock(answer = RETURNS_DEEP_STUBS) final InetAddressedMessage msg) {
        when(msg.touch(any())).thenReturn(msg);

        final EmbeddedChannel channel = new EmbeddedChannel(new UnresolvedOverlayMessageHandler());

        channel.writeOutbound(msg);

        assertEquals(msg, channel.readOutbound());

        channel.close();
    }
}
