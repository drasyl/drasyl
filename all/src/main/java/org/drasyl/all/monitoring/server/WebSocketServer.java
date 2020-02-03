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

import org.drasyl.all.Drasyl;
import org.drasyl.all.monitoring.Aggregator;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * An HTTP server which serves Web Socket requests at:
 * <p>
 * http://localhost:{@link #port}/websocket
 * <p>
 * Open your browser at <a href="http://localhost:{@link #port}/">http://localhost:{@link #port}/</a>, then the
 * monitoring page will be loaded
 * and a Web Socket connection will be made automatically.
 */
public final class WebSocketServer {
    private static final Logger LOG = LoggerFactory.getLogger(WebSocketServer.class);
    private final Aggregator aggregator;
    private final boolean debuggingMode;
    private final String websocketPath;
    private final String baseDir;
    private final String token;
    private final int port;

    private Channel channel;

    public WebSocketServer(int port,
                           boolean debuggingMode, Aggregator aggregator, String token, String websocketPath) {
        this.debuggingMode = debuggingMode;
        this.websocketPath = websocketPath;
        this.aggregator = aggregator;
        this.baseDir = getBaseDir();
        this.token = token;
        this.port = port;
    }

    /*
     * To be independent it uses it owns EventLoopGroups.
     */
    public void start() throws InterruptedException {
        ServerBootstrap b = new ServerBootstrap();
        b.group(new NioEventLoopGroup(), new NioEventLoopGroup())
                .channel(NioServerSocketChannel.class)
//                .handler(new LoggingHandler(LogLevel.INFO))
                .childHandler(new WebSocketServerInitializer(baseDir, aggregator, token, websocketPath));

        channel = b.bind(port).sync().channel();

        LOG.info("WebSocket for monitoring was started on port '{}' using path '{}'", port, baseDir);
    }

    public void stop() {
        channel.close().awaitUninterruptibly();
    }

    /**
     * Returns the baseDir for the monitoring page.
     */
    private String getBaseDir() {
        File dir;
        if (debuggingMode) {
            dir = new File("all/monitoring-page/dist");
        } else {
            dir = new File("public/");
            if (!dir.exists()) {
                String path = Drasyl.class.getProtectionDomain().getCodeSource().getLocation().getPath();
                String decodedPath = URLDecoder.decode(path, StandardCharsets.UTF_8);
                dir = new File(decodedPath + "/dist");
            }
        }

        return dir.getAbsolutePath();
    }
}
