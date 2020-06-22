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
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import org.drasyl.peer.connection.message.AbstractMessage;
import org.drasyl.peer.connection.message.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class MessageEncoderTest {
    private Message message;
    private EmbeddedChannel channel;

    @BeforeEach
    void setUp() {
        message = new AbstractMessage() {
            @Override
            public String getId() {
                return "abc";
            }
        };

        ChannelHandler handler = MessageEncoder.INSTANCE;
        channel = new EmbeddedChannel(handler);
    }

    @Test
    void shouldSerializeOutboundMessageToJsonString() {
        channel.writeOutbound(message);
        channel.flush();

        byte[] binary = new byte[]{
                123,
                34,
                64,
                116,
                121,
                112,
                101,
                34,
                58,
                34,
                77,
                101,
                115,
                115,
                97,
                103,
                101,
                69,
                110,
                99,
                111,
                100,
                101,
                114,
                84,
                101,
                115,
                116,
                36,
                49,
                34,
                44,
                34,
                105,
                100,
                34,
                58,
                34,
                97,
                98,
                99,
                34,
                125
        };

        BinaryWebSocketFrame frame = channel.readOutbound();

        byte[] actual = new byte[frame.content().readableBytes()];
        frame.content().readBytes(actual);

        assertArrayEquals(binary, actual);
    }
}
