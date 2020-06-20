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
package org.drasyl.peer.connection.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.drasyl.DrasylNodeConfig;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public class NodeServerChannelBootstrap {
    private final DrasylNodeConfig config;
    private final NodeServer server;
    private final ServerBootstrap serverBootstrap;
    private final ChannelInitializer<SocketChannel> relayServerInitializer;

    public NodeServerChannelBootstrap(DrasylNodeConfig config,
                                      NodeServer server,
                                      ServerBootstrap serverBootstrap) throws NodeServerException {
        this.config = config;
        this.server = server;
        this.serverBootstrap = serverBootstrap;
        String channelInitializer = config.getServerChannelInitializer();

        try {
            this.relayServerInitializer = getChannelInitializer(config, server, channelInitializer);
        }
        catch (ClassNotFoundException e) {
            throw new NodeServerException("The given channel initializer can't be found: '" + channelInitializer + "'");
        }
        catch (NoSuchMethodException e) {
            throw new NodeServerException("The given channel initializer has not the correct signature: '" + channelInitializer + "'");
        }
        catch (IllegalAccessException e) {
            throw new NodeServerException("Can't access the given channel initializer: '" + channelInitializer + "'");
        }
        catch (InvocationTargetException e) {
            throw new NodeServerException("Can't invoke the given channel initializer: '" + channelInitializer + "'");
        }
        catch (InstantiationException e) {
            throw new NodeServerException("Can't instantiate the given channel initializer: '" + channelInitializer + "'");
        }
    }

    private ChannelInitializer<SocketChannel> getChannelInitializer(DrasylNodeConfig config,
                                                                    NodeServer server,
                                                                    String className) throws ClassNotFoundException,
            NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        Class<?> c = Class.forName(className);
        Constructor<?> cons = c.getConstructor(DrasylNodeConfig.class, NodeServer.class);

        return (ChannelInitializer<SocketChannel>) cons.newInstance(config, server);
    }

    public ChannelFuture getChannel() throws NodeServerException {
        try {
            return serverBootstrap
                    .group(server.bossGroup, server.workerGroup)
                    .channel(NioServerSocketChannel.class)
//                .handler(new LoggingHandler(LogLevel.INFO))
                    .childHandler(relayServerInitializer)
                    .bind(config.getServerBindHost(), config.getServerBindPort());
        }
        catch (IllegalArgumentException e) {
            throw new NodeServerException("Unable to get channel: " + e.getMessage());
        }
    }
}
