/*
 * Copyright (c) 2020.
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
package org.drasyl.peer.connection.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import org.drasyl.crypto.CryptoException;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.peer.connection.message.ErrorMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.drasyl.peer.connection.message.ErrorMessage.Error.ERROR_IDENTITY_COLLISION;
import static org.drasyl.util.JSONUtil.JACKSON_WRITER;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class MessageDecoderTest {
    private EmbeddedChannel channel;

    @BeforeEach
    void setUp() {
        final ChannelHandler handler = MessageDecoder.INSTANCE;
        channel = new EmbeddedChannel(handler);
    }

    @Test
    void shouldDeserializeInboundJsonStringToMessage() throws JsonProcessingException, CryptoException {
        final byte[] binary = JACKSON_WRITER.writeValueAsBytes(new ErrorMessage(CompressedPublicKey.of("034a450eb7955afb2f6538433ae37bd0cbc09745cf9df4c7ccff80f8294e6b730d"), ProofOfWork.of(3556154), ERROR_IDENTITY_COLLISION));

        channel.writeInbound(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(binary)));
        channel.flush();

        assertThat(channel.readInbound(), instanceOf(ErrorMessage.class));
    }

    @Test
    void ShouldThrowExceptionIfInboundJsonStringDeserializationFail() throws JsonProcessingException {
        final byte[] binary = JACKSON_WRITER.writeValueAsBytes("invalid");

        final BinaryWebSocketFrame frame = new BinaryWebSocketFrame(Unpooled.wrappedBuffer(binary));
        assertThrows(DecoderException.class, () -> channel.writeInbound(frame));
    }
}