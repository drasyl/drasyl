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

package org.drasyl.core.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public class RelayBootstrap {
    private final RelayServer relay;
    private final ServerBootstrap serverBootstrap;
    private final RelayServerConfig config;
    private final ChannelInitializer relayServerInitializer;

    public RelayBootstrap(RelayServer relay, ServerBootstrap serverBootstrap, RelayServerConfig config) throws RelayServerException {
        this.relay = relay;
        this.serverBootstrap = serverBootstrap;
        this.config = config;

        try {
            this.relayServerInitializer = getChannelInitializer(relay, config.getChannelInitializer());
        } catch (ClassNotFoundException e) {
            throw new RelayServerException("The given channel initializer can't be found: '" + config.getChannelInitializer() + "'");
        } catch (NoSuchMethodException e) {
            throw new RelayServerException("The given channel initializer has not the correct signature: '" + config.getChannelInitializer() + "'");
        } catch (IllegalAccessException e) {
            throw new RelayServerException("Can't access the given channel initializer: '" + config.getChannelInitializer() + "'");
        } catch (InvocationTargetException e) {
            throw new RelayServerException("Can't invoke the given channel initializer: '" + config.getChannelInitializer() + "'");
        } catch (InstantiationException e) {
            throw new RelayServerException("Can't instantiate the given channel initializer: '" + config.getChannelInitializer() + "'");
        }
    }

    public Channel getChannel() throws InterruptedException {
        return serverBootstrap
                .group(relay.bossGroup, relay.workerGroup)
                .channel(NioServerSocketChannel.class)
//                .handler(new LoggingHandler(LogLevel.INFO))
                .childHandler(relayServerInitializer)
                .bind(config.getRelayEntrypoint().getPort())
                .sync()
                .channel();
    }

    private ChannelInitializer getChannelInitializer(RelayServer relay, String className) throws ClassNotFoundException,
            NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        Class<?> c = Class.forName(className);
        Constructor<?> cons = c.getConstructor(RelayServer.class);

        return (ChannelInitializer) cons.newInstance(relay);
    }
}
