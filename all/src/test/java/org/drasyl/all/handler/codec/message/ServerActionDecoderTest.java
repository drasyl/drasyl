/*
 * Copyright (c) 2020
 *
 * This file is part of drasyl.
 *
 * drasyl is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * drasyl is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.drasyl.all.handler.codec.message;

import org.drasyl.all.messages.Leave;
import org.drasyl.all.actions.messages.ServerActionLeave;
import io.netty.channel.ChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class ServerActionDecoderTest {
    private EmbeddedChannel channel;

    @BeforeEach
    void setUp() {
        ChannelHandler handler = ServerActionMessageDecoder.INSTANCE;
        channel = new EmbeddedChannel(handler);
    }

    @Test
    void deserializeFromJson() {
        String json = "{\"type\":\"Leave\",\"messageID\":\"123\"}";

        channel.writeInbound(new TextWebSocketFrame(json));
        channel.flush();

        Object o = channel.readInbound();

        assertThat(o, instanceOf(Leave.class));
        assertThat(o, instanceOf(ServerActionLeave.class));
    }

    @Test
    void deserializeFromInvalidJson() {
        String json = "invalid";

        assertThrows(DecoderException.class, () -> {
            channel.writeInbound(new TextWebSocketFrame(json));
            channel.flush();
        });
    }
}