package org.drasyl.peer.connection.superpeer;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.drasyl.DrasylNodeConfig;
import org.drasyl.util.WebsocketUtil;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.concurrent.CompletableFuture;

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

    public Channel getChannel() throws SuperPeerClientException {
        ChannelFuture channelFuture = new Bootstrap()
                .group(workerGroup)
                .channel(NioSocketChannel.class)
                .handler(superPeerClientInitializer)
                .connect(endpoint.getHost(), WebsocketUtil.websocketPort(endpoint));
        channelFuture.awaitUninterruptibly();

        if (channelFuture.isSuccess()) {
            Channel channel = channelFuture.channel();
            superPeerClientInitializer.connectedFuture().join(); // TODO: handle join failures!?
            return channel;
        }
        else {
            throw new SuperPeerClientException(channelFuture.cause());
        }
    }
}
