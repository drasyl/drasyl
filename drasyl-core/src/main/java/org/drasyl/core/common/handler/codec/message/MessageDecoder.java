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

package org.drasyl.core.common.handler.codec.message;

import org.drasyl.core.common.message.Message;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.handler.codec.MessageToMessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

import java.io.IOException;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Decodes a {@link String} into a {@link Message} object.
 */
@Sharable
public class MessageDecoder extends MessageToMessageDecoder<TextWebSocketFrame> implements ChannelInboundHandler {
    private static final Logger LOG = LoggerFactory.getLogger(MessageDecoder.class);
    public static final MessageDecoder INSTANCE = new MessageDecoder();
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    public static final String MESSAGE_DECODER = "messageDecoder";

    private MessageDecoder() {
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, TextWebSocketFrame msg, List<Object> out) {
        if (LOG.isDebugEnabled())
            LOG.debug("[{}]: Receive Message '{}'", ctx.channel().id().asShortText(), msg.text());

        try {
            Message message = requireNonNull(JSON_MAPPER.readValue(msg.text(), Message.class));
            out.add(message);
        } catch (IOException e) {
            LOG.warn("[{}]: Invalid Message: '{}'", ctx.channel().id().asShortText(), msg.text());
            throw new IllegalArgumentException("Your request dit not contain a valid Message: " + e.getMessage());
        }
    }
}
