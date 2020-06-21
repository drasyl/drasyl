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
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.drasyl.peer.connection.message.Message;
import org.drasyl.util.LoggingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.drasyl.util.JSONUtil.JACKSON_MAPPER;

/**
 * Encodes a {@link Message} into a {@link String} object.
 */
@Sharable
public class MessageEncoder extends MessageToMessageEncoder<Message> {
    public static final MessageEncoder INSTANCE = new MessageEncoder();
    public static final String MESSAGE_ENCODER = "messageEncoder";
    private static final Logger LOG = LoggerFactory.getLogger(MessageEncoder.class);

    private MessageEncoder() {
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Message msg, List<Object> out) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("[{}]: Send Message '{}'", ctx.channel().id().asShortText(), msg);
        }

        try {
            String json = JACKSON_MAPPER.writeValueAsString(msg);

            out.add(new TextWebSocketFrame(json));
        }
        catch (JsonProcessingException e) {
            LOG.error("[{}]: Unable to serialize '{}'", ctx.channel().id().asShortText(), LoggingUtil.sanitizeLogArg(msg));
            throw new IllegalArgumentException("Message could not be serialized. This could indicate a bug in drasyl: " + e.getMessage());
        }
    }
}
