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

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import org.drasyl.DrasylNodeConfig;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public class NodeServerChannelBootstrap {
    private final ChannelInitializer<SocketChannel> channelInitializer;

    public NodeServerChannelBootstrap(DrasylNodeConfig config,
                                      NodeServer server) throws NodeServerException {
        Class<? extends ChannelInitializer<SocketChannel>> channelInitializerClazz = config.getServerChannelInitializer();

        try {
            this.channelInitializer = initiateChannelInitializer(config, server, channelInitializerClazz);
        }
        catch (NoSuchMethodException e) {
            throw new NodeServerException("The given channel initializer has not the correct signature: '" + channelInitializerClazz + "'");
        }
        catch (IllegalAccessException e) {
            throw new NodeServerException("Can't access the given channel initializer: '" + channelInitializerClazz + "'");
        }
        catch (InvocationTargetException e) {
            throw new NodeServerException("Can't invoke the given channel initializer: '" + channelInitializerClazz + "'");
        }
        catch (InstantiationException e) {
            throw new NodeServerException("Can't instantiate the given channel initializer: '" + channelInitializerClazz + "'");
        }
    }

    private ChannelInitializer<SocketChannel> initiateChannelInitializer(DrasylNodeConfig config,
                                                                         NodeServer server,
                                                                         Class<? extends ChannelInitializer<SocketChannel>> clazz) throws
            NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        Constructor<?> cons = clazz.getConstructor(DrasylNodeConfig.class, NodeServer.class);

        return (ChannelInitializer<SocketChannel>) cons.newInstance(config, server);
    }

    public ChannelInitializer<SocketChannel> getChannelInitializer() {
        return channelInitializer;
    }
}
