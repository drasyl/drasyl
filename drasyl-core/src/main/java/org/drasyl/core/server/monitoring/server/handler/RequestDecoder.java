/*
 * Copyright (c) 2020
 *
 * This file is part of Relayserver.
 *
 * Relayserver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Relayserver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Relayserver.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.drasyl.core.server.monitoring.server.handler;

import org.drasyl.core.server.monitoring.models.WebsocketRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;

import java.util.List;
import java.util.Objects;

@ChannelHandler.Sharable
public class RequestDecoder extends MessageToMessageDecoder<WebSocketFrame> implements ChannelInboundHandler {
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    public static final RequestDecoder INSTANCE = new RequestDecoder();

    private RequestDecoder() {
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, WebSocketFrame frame, List<Object> out) throws Exception {
        if (frame instanceof TextWebSocketFrame) {
            String request = null;
            try {
                request = ((TextWebSocketFrame) frame).text();
                WebsocketRequest req = Objects.requireNonNull(JSON_MAPPER.readValue(request, WebsocketRequest.class));
                out.add(req);
            } catch (Exception e) {
                String message = "unsupported request";
                throw new UnsupportedOperationException(message+": "+request);
            }
        } else {
            String message = "unsupported frame type: " + frame.getClass().getName();
            throw new UnsupportedOperationException(message);
        }
    }
}