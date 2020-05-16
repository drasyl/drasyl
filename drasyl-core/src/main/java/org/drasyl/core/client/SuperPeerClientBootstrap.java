package org.drasyl.core.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.drasyl.core.client.handler.SuperPeerClientInitializer;
import org.drasyl.core.node.DrasylNodeConfig;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;

import static org.drasyl.core.common.util.WebsocketUtil.websocketPort;

public class SuperPeerClientBootstrap {
    private final DrasylNodeConfig config;
    private final EventLoopGroup workerGroup;
    private final URI endpoint;
    private final SuperPeerClientInitializer superPeerClientInitializer;
    private final SuperPeerClient superPeerClient;

    public SuperPeerClientBootstrap(DrasylNodeConfig config,
                                    EventLoopGroup workerGroup,
                                    URI endpoint,
                                    SuperPeerClient superPeerClient) throws SuperPeerClientException {
        this.config = config;
        this.workerGroup = workerGroup;
        this.endpoint = endpoint;
        this.superPeerClient = superPeerClient;
        String channelInitializer = config.getSuperPeerChannelInitializer();

        try {
            this.superPeerClientInitializer = getChannelInitializer(channelInitializer);
        }
        catch (ClassNotFoundException e) {
            throw new SuperPeerClientException("The given channel initializer can't be found: '" + channelInitializer + "'");
        }
        catch (NoSuchMethodException e) {
            throw new SuperPeerClientException("The given channel initializer has not the correct signature: '" + channelInitializer + "'");
        }
        catch (IllegalAccessException e) {
            throw new SuperPeerClientException("Can't access the given channel initializer: '" + channelInitializer + "'");
        }
        catch (InvocationTargetException e) {
            throw new SuperPeerClientException("Can't invoke the given channel initializer: '" + channelInitializer + "'");
        }
        catch (InstantiationException e) {
            throw new SuperPeerClientException("Can't instantiate the given channel initializer: '" + channelInitializer + "'");
        }
    }

    private SuperPeerClientInitializer getChannelInitializer(String className) throws ClassNotFoundException,
            NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        Class<?> c = Class.forName(className);
        Constructor<?> cons = c.getConstructor(DrasylNodeConfig.class, URI.class, SuperPeerClient.class);

        return (SuperPeerClientInitializer) cons.newInstance(config, endpoint, superPeerClient);
    }

    public Channel getChannel() {
        Channel channel = new Bootstrap()
                .group(workerGroup)
                .channel(NioSocketChannel.class)
                .handler(superPeerClientInitializer)
                .connect(endpoint.getHost(), websocketPort(endpoint))
                .syncUninterruptibly()
                .channel();
        superPeerClientInitializer.connectedFuture().join();

        return channel;
    }
}
