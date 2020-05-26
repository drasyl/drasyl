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

import io.netty.channel.ChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.drasyl.peer.connection.message.QuitMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MessageDecoderTest {
    private EmbeddedChannel channel;

    @BeforeEach
    void setUp() {
        ChannelHandler handler = MessageDecoder.INSTANCE;
        channel = new EmbeddedChannel(handler);
    }

    @Test
    void shouldDeserializeInboundJsonStringToMessage() {
        String json = "{\"@type\":\"" + QuitMessage.class.getSimpleName() + "\",\"id\":\"123\"}";

        channel.writeInbound(new TextWebSocketFrame(json));
        channel.flush();

        assertThat(channel.readInbound(), instanceOf(QuitMessage.class));
    }

    @Test
    void ShouldThrowExceptionIfInboundJsonStringDeserializationFail() {
        String json = "invalid";

        assertThrows(DecoderException.class, () -> channel.writeInbound(new TextWebSocketFrame(json)));
    }
}
