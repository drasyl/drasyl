package org.drasyl.core.client.transport.relay;

import akka.actor.ExtendedActorSystem;
import com.typesafe.config.Config;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.drasyl.core.client.P2PActorRef;
import org.drasyl.core.client.transport.*;
import org.drasyl.core.client.transport.handler.EnvelopeMessageHandler;
import org.drasyl.core.client.transport.relay.handler.RelayJoinHandler;
import org.drasyl.core.client.transport.relay.handler.RelayMessageHandler;
import org.drasyl.core.client.transport.relay.handler.RelayP2PTransportChannelInitializer;
import org.drasyl.core.common.message.LeaveMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static java.util.Objects.requireNonNull;

/**
 * Defines a channel, which deliver all messages between actors via a single relay server.
 */
public class RelayP2PTransportChannel implements P2PTransportChannel {
    private static final Logger log = LoggerFactory.getLogger(RelayP2PTransportChannel.class);
    private final P2PTransport transport;
    private final RelayP2PTransportChannelProperties properties;

    private final CompletableFuture<Void> shutdownFuture;

    private final Bootstrap bootstrap;
    protected Channel nettyChannel = null;
    private final RelayP2PTransportChannelInitializer initializer;

    RelayP2PTransportChannel(P2PTransport transport, RelayP2PTransportChannelProperties properties, ExtendedActorSystem system) {
        this.transport = requireNonNull(transport);
        this.properties = requireNonNull(properties);
        this.shutdownFuture = new CompletableFuture<>();

        initializer = new RelayP2PTransportChannelInitializer(
                new EnvelopeMessageHandler(system, transport.getProvider(), transport.defaultAddress()),
                new RelayMessageHandler(),
                new RelayJoinHandler(properties.getPublicKey(), properties.getEndpoints(), properties.getJoinTimeout()),
                new SimpleChannelInboundHandler<InboundMessageEnvelope>() {
                    @Override
                    protected void channelRead0(ChannelHandlerContext ctx, InboundMessageEnvelope message) {
                        receive(message);
                    }

                    @Override
                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                        shutdownFuture.completeExceptionally(new P2PTransportChannelException("An error has occurred in the connection to the relay server. Close connection: " + cause.getMessage(), cause));
                        ctx.close();
                    }
                }, 256, Duration.ofMinutes(1), (short) 3,1000000, properties.getRelayUrl()
        );
        // .addLast(new EnvelopeMessageHandler(system, transport.getProvider(), transport.defaultAddress()))
        //                                .addLast(new RelayMessageHandler())
        bootstrap = new Bootstrap()
                .group(new NioEventLoopGroup())
                .channel(NioSocketChannel.class)
//                .handler(new LoggingHandler(LogLevel.DEBUG))
                .remoteAddress(properties.getRelayUrl().getHost(), properties.getRelayUrl().getPort())
                .handler(initializer);
    }

    public RelayP2PTransportChannel(String system, Config config, P2PTransport transport, ExtendedActorSystem actorSystem) throws URISyntaxException {
        this(transport, new RelayP2PTransportChannelProperties(system, config), actorSystem);
    }

    @Override
    public CompletableFuture<Void> start() {
        return CompletableFuture.runAsync(() -> {
            try {
                nettyChannel = bootstrap.connect().syncUninterruptibly().channel();
                nettyChannel.closeFuture()
                        .addListener((ChannelFutureListener) channelFuture ->
                        shutdownFuture.complete(null)
                );
                initializer.getCurrentJoinHandler()
                        .joinFuture().syncUninterruptibly();
            }
            catch (Exception e) {
                throw new CompletionException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> closeFuture() {
        return shutdownFuture;
    }


    @Override
    public CompletableFuture<Void> shutdown() {
        if (nettyChannel != null) {
            nettyChannel.writeAndFlush(new LeaveMessage());
            return closeFuture();
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void send(OutboundMessageEnvelope outboundMessage) throws P2PTransportChannelException {
        if(nettyChannel.isOpen()) {
            try {
                    log.debug("Sending: " + outboundMessage);
                    nettyChannel.writeAndFlush(outboundMessage);
            }
            catch (Exception e) {
                throw new P2PTransportChannelException(e);
            }
        } else {
            throw new P2PTransportChannelException("No netty channel is currently open!");
        }
    }

    @Override
    public void receive(InboundMessageEnvelope inboundMessage) {
        log.debug("Receiving: {}", inboundMessage);
        transport.receive(inboundMessage);
    }

    @Override
    public boolean accept(P2PActorRef recipient) {
        // Always return <code>true</code>, because the relay can sendMSG to all clients
        return true;
    }
}
