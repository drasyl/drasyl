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
package org.drasyl.peer.connection.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import io.reactivex.rxjava3.subjects.Subject;
import org.drasyl.DrasylConfig;
import org.drasyl.event.Event;
import org.drasyl.identity.Identity;
import org.drasyl.messenger.Messenger;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.superpeer.SuperPeerClientChannelInitializer;
import org.drasyl.peer.connection.superpeer.SuperPeerClientEnvironment;
import org.drasyl.peer.connection.superpeer.SuperPeerClientException;
import org.drasyl.util.DrasylFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * This class represents the link between <code>DrasylNode</code> and the super peer. It is
 * responsible for maintaining the connection to the super peer and updates the data of the super
 * peer in {@link PeersManager}.
 */
@SuppressWarnings({ "java:S107", "java:S4818" })
public class SuperPeerClient extends NodeClient {
    private static final Logger LOG = LoggerFactory.getLogger(SuperPeerClient.class);

    SuperPeerClient(DrasylConfig config,
                    EventLoopGroup workerGroup,
                    Set<URI> endpoints,
                    AtomicBoolean opened,
                    AtomicInteger nextEndpointPointer,
                    AtomicInteger nextRetryDelayPointer,
                    Supplier<Bootstrap> bootstrapSupplier,
                    Subject<Boolean> connected,
                    DrasylFunction<URI, ChannelInitializer<SocketChannel>> channelInitializerSupplier,
                    ChannelInitializer<SocketChannel> channelInitializer,
                    Channel channel) {
        super(
                config.getSuperPeerRetryDelays(),
                workerGroup,
                endpoints,
                opened,
                nextEndpointPointer,
                nextRetryDelayPointer,
                bootstrapSupplier,
                connected,
                channelInitializerSupplier,
                channelInitializer,
                channel
        );
    }

    protected SuperPeerClient(DrasylConfig config,
                              EventLoopGroup workerGroup,
                              Subject<Boolean> connected,
                              DrasylFunction<URI, ChannelInitializer<SocketChannel>> channelInitializerSupplier) {
        super(
                config.getSuperPeerRetryDelays(),
                workerGroup,
                config.getSuperPeerEndpoints(),
                connected,
                channelInitializerSupplier
        );
    }

    public SuperPeerClient(DrasylConfig config,
                           Supplier<Identity> identitySupplier,
                           PeersManager peersManager,
                           Messenger messenger,
                           EventLoopGroup workerGroup,
                           Consumer<Event> eventConsumer) {
        this(
                config,
                identitySupplier,
                peersManager,
                messenger,
                workerGroup,
                BehaviorSubject.createDefault(false),
                eventConsumer
        );
    }

    private SuperPeerClient(DrasylConfig config,
                            Supplier<Identity> identitySupplier,
                            PeersManager peersManager,
                            Messenger messenger,
                            EventLoopGroup workerGroup,
                            Subject<Boolean> connected,
                            Consumer<Event> eventConsumer) {
        super(
                config.getSuperPeerRetryDelays(),
                workerGroup,
                config.getSuperPeerEndpoints(),
                connected,
                endpoint -> initiateChannelInitializer(new SuperPeerClientEnvironment(config, identitySupplier, endpoint, messenger, peersManager, connected, eventConsumer), config.getSuperPeerChannelInitializer())
        );
    }

    private static SuperPeerClientChannelInitializer initiateChannelInitializer(
            SuperPeerClientEnvironment environment,
            Class<? extends ChannelInitializer<SocketChannel>> clazz) throws SuperPeerClientException {
        try {
            Constructor<?> constructor = clazz.getConstructor(SuperPeerClientEnvironment.class);
            return (SuperPeerClientChannelInitializer) constructor.newInstance(environment);
        }
        catch (NoSuchMethodException e) {
            throw new SuperPeerClientException("The given channel initializer has not the correct signature: '" + clazz + "'");
        }
        catch (IllegalAccessException e) {
            throw new SuperPeerClientException("Can't access the given channel initializer: '" + clazz + "'");
        }
        catch (InvocationTargetException e) {
            throw new SuperPeerClientException("Can't invoke the given channel initializer: '" + clazz + "'");
        }
        catch (InstantiationException e) {
            throw new SuperPeerClientException("Can't instantiate the given channel initializer: '" + clazz + "'");
        }
    }

    @Override
    protected Logger getLogger() {
        return LOG;
    }
}
