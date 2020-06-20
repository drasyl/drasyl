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

package org.drasyl.peer.connection.superpeer;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import org.drasyl.DrasylNodeConfig;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;

public class SuperPeerClientChannelBootstrap {
    private final DrasylNodeConfig config;
    private final URI endpoint;
    private final SuperPeerClientChannelInitializer channelInitializer;
    private final SuperPeerClient client;

    public SuperPeerClientChannelBootstrap(DrasylNodeConfig config,
                                           SuperPeerClient client,
                                           URI endpoint) throws SuperPeerClientException {
        this.config = config;
        this.endpoint = endpoint;
        this.client = client;
        Class<? extends ChannelInitializer<SocketChannel>> channelInitializerClazz = config.getSuperPeerChannelInitializer();

        try {
            this.channelInitializer = initiateChannelInitializer(channelInitializerClazz);
        }
        catch (NoSuchMethodException e) {
            throw new SuperPeerClientException("The given channel initializer has not the correct signature: '" + channelInitializerClazz + "'");
        }
        catch (IllegalAccessException e) {
            throw new SuperPeerClientException("Can't access the given channel initializer: '" + channelInitializerClazz + "'");
        }
        catch (InvocationTargetException e) {
            throw new SuperPeerClientException("Can't invoke the given channel initializer: '" + channelInitializerClazz + "'");
        }
        catch (InstantiationException e) {
            throw new SuperPeerClientException("Can't instantiate the given channel initializer: '" + channelInitializerClazz + "'");
        }
    }

    private SuperPeerClientChannelInitializer initiateChannelInitializer(Class<? extends ChannelInitializer<SocketChannel>> clazz) throws
            NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        Constructor<?> cons = clazz.getConstructor(DrasylNodeConfig.class, SuperPeerClient.class, URI.class);

        return (SuperPeerClientChannelInitializer) cons.newInstance(config, client, endpoint);
    }

    public ChannelInitializer<SocketChannel> getChannelInitializer() {
        return channelInitializer;
    }
}
