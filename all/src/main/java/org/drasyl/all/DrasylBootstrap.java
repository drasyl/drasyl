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

package org.drasyl.all;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public class DrasylBootstrap {
    private final Drasyl relay;
    private final ServerBootstrap serverBootstrap;
    private final DrasylConfig config;
    private final ChannelInitializer relayServerInitializer;

    public DrasylBootstrap(Drasyl relay, ServerBootstrap serverBootstrap, DrasylConfig config) throws DrasylException {
        this.relay = relay;
        this.serverBootstrap = serverBootstrap;
        this.config = config;

        try {
            this.relayServerInitializer = getChannelInitializer(relay, config.getChannelInitializer());
        } catch (ClassNotFoundException e) {
            throw new DrasylException("The given channel initializer can't be found: '" + config.getChannelInitializer() + "'");
        } catch (NoSuchMethodException e) {
            throw new DrasylException("The given channel initializer has not the correct signature: '" + config.getChannelInitializer() + "'");
        } catch (IllegalAccessException e) {
            throw new DrasylException("Can't access the given channel initializer: '" + config.getChannelInitializer() + "'");
        } catch (InvocationTargetException e) {
            throw new DrasylException("Can't invoke the given channel initializer: '" + config.getChannelInitializer() + "'");
        } catch (InstantiationException e) {
            throw new DrasylException("Can't instantiate the given channel initializer: '" + config.getChannelInitializer() + "'");
        }
    }

    public Channel getChannel() throws InterruptedException {
        return serverBootstrap
                .group(relay.bossGroup, relay.workerGroup)
                .channel(NioServerSocketChannel.class)
//                .handler(new LoggingHandler(LogLevel.INFO))
                .childHandler(relayServerInitializer)
                .bind(config.getRelayPort())
                .sync()
                .channel();
    }

    private ChannelInitializer getChannelInitializer(Drasyl relay, String className) throws ClassNotFoundException,
            NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        Class<?> c = Class.forName(className);
        Constructor<?> cons = c.getConstructor(Drasyl.class);

        return (ChannelInitializer) cons.newInstance(relay);
    }
}
