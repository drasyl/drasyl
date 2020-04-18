package city.sane.akka.p2p.transport.direct;

import akka.actor.ExtendedActorSystem;
import city.sane.akka.p2p.transport.InboundMessageEnvelope;
import city.sane.akka.p2p.transport.P2PTransport;
import city.sane.akka.p2p.transport.P2PTransportChannel;
import city.sane.akka.p2p.transport.direct.handler.DirectChannelInitializer;
import city.sane.akka.p2p.transport.handler.EnvelopeMessageHandler;
import com.typesafe.config.Config;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.util.concurrent.CompletableFuture;

/**
 * A channel for direct connections to peers
 */
public class BundledDirectP2PTransportChannel extends AbstractDirectP2PTransportChannel implements P2PTransportChannel {
    private static final Logger log = LoggerFactory.getLogger(BundledDirectP2PTransportChannel.class);

    private final Bootstrap bootstrap;
    private final DirectP2PTransportChannelProperties properties;
    private final P2PTransport transport;
    private final ExtendedActorSystem system;
    private final ServerBootstrap serverBootstrap;
    private final NioEventLoopGroup bossGroup;
    private final NioEventLoopGroup workerGroup;
    private CompletableFuture<Void> shutdownFuture;

    BundledDirectP2PTransportChannel(P2PTransport transport,
                                     DirectP2PTransportChannelProperties properties,
                                     ExtendedActorSystem system) {
        super();
        this.transport = transport;
        this.system = system;
        this.shutdownFuture = new CompletableFuture<>();
        this.properties = properties;

        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        bootstrap = new Bootstrap()
                .group(workerGroup)
                .channel(NioSocketChannel.class)
                .handler(new LoggingHandler(LogLevel.DEBUG))
                .handler(new DirectChannelInitializer(
                        system.name(), transport,
                        new EnvelopeMessageHandler(system, transport.getProvider(), transport.defaultAddress()),
                        this,
                        true));



        serverBootstrap = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .handler(new LoggingHandler(LogLevel.INFO))
                .childHandler(new DirectChannelInitializer(
                        system.name(), transport,
                        new EnvelopeMessageHandler(system, transport.getProvider(), transport.defaultAddress()),
                        this,
                        false))
                .option(ChannelOption.SO_BACKLOG, 128)
                .childOption(ChannelOption.SO_KEEPALIVE, true);

    }

    // constructor call in P2PTransport::new from class reflection via className in config
    @SuppressWarnings("unused")
    public BundledDirectP2PTransportChannel(String system,
                                            Config config,
                                            P2PTransport transport,
                                            ExtendedActorSystem actorSystem) {
        this(transport, new DirectP2PTransportChannelProperties(config), actorSystem);
    }

    @Override
    public synchronized CompletableFuture<Void> start() {
        // start listening for peers
        serverBootstrap.bind(properties.getListenPort());
        log.debug("BundledDirectP2PTransportChannel now listens on port {}.", properties.getListenPort());

        // channel can receive after start but not send
        // after channel calls this::addPeer -> sending will be possible
        // TODO peer connections should be retried on failure (adjusting RetryStrategy for peer connections)
        CompletableFuture<?>[] startFutures = properties.getInitialPeers().parallelStream()
                .map(this::connectTo)
                .map(cf -> CompletableFuture.runAsync(() -> cf.syncUninterruptibly().channel()))
                .toArray(CompletableFuture<?>[]::new);

        return CompletableFuture.allOf(startFutures);
    }



    @Override
    public CompletableFuture<Void> closeFuture() {
        return shutdownFuture;
    }


    protected CompletableFuture<Void> shutdownChannel(Channel channel) {
        return CompletableFuture.runAsync(() -> channel
                .close()
                .syncUninterruptibly());
    }



    /**
     * Connects to a new peer. The systemname of the peer is ignored.
     *
     * @param peerAddress the peer to which this transport channel should connect to
     * @return future for the connection (note: this P2PTransportChannel will not immediately be available for sending)
     */
    public ChannelFuture connectTo(SocketAddress peerAddress) {
        return bootstrap.connect(peerAddress);
    }

    @Override
    public synchronized CompletableFuture<Void> shutdown() {
        // shutdown server
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();

        shutdownAllPeers()
                .thenRun(() -> shutdownFuture.complete(null));

        return shutdownFuture;
    }


    @Override
    public void receive(InboundMessageEnvelope inboundMessage) {
        log.debug("Receiving: {}", inboundMessage);
        transport.receive(inboundMessage);
    }

    public void notifyError(String format, Throwable cause) {
        transport.notifyError(format, cause);
    }

}
