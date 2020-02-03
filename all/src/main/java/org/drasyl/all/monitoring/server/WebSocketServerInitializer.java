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

package org.drasyl.all.monitoring.server;

import org.drasyl.all.monitoring.Aggregator;
import org.drasyl.all.monitoring.server.handler.*;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler;
import org.drasyl.all.monitoring.server.handler.*;

public class WebSocketServerInitializer extends ChannelInitializer<SocketChannel> {
    private final String websocketPath;
    private final Aggregator aggregator;
    private final String token;
    private final String baseDir;

    public WebSocketServerInitializer(String baseDir, Aggregator aggregator, String token, String websocketPath) {
        this.websocketPath = websocketPath;
        this.aggregator = aggregator;
        this.baseDir = baseDir;
        this.token = token;
    }

    @Override
    public void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();

        pipeline.addLast(new HttpServerCodec());
        pipeline.addLast(new HttpObjectAggregator(65536));
        pipeline.addLast(new WebSocketServerCompressionHandler());
        pipeline.addLast(new WebSocketServerProtocolHandler(websocketPath, null, true));
        pipeline.addLast(new HttpStaticFileServerHandler(baseDir));
        pipeline.addLast(RequestDecoder.INSTANCE);
        pipeline.addLast(ResponseEncoder.INSTANCE);
        pipeline.addLast(new AuthHandler(token));
        pipeline.addLast(new RequestHandler(aggregator));
    }
}
