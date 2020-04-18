package city.sane.akka.p2p.transport.direct;

import akka.actor.ExtendedActorSystem;
import city.sane.akka.p2p.transport.InboundMessageEnvelope;
import city.sane.akka.p2p.transport.P2PTransport;
import city.sane.akka.p2p.transport.P2PTransportChannelException;
import city.sane.akka.p2p.transport.direct.handler.DirectChannelInitializer;
import city.sane.akka.p2p.transport.handler.EnvelopeMessageHandler;
import com.google.common.collect.Lists;
import com.typesafe.config.Config;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.epoll.EpollDomainSocketChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerDomainSocketChannel;
import io.netty.channel.unix.DomainSocketAddress;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.SocketAddress;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;

/**
 * A channel for direct connections to peers
 */
public class UnixSocketP2PTransportChannel extends AbstractDirectP2PTransportChannel {
    private static final Logger log = LoggerFactory.getLogger(UnixSocketP2PTransportChannel.class);
    private Bootstrap bootstrap;
    private final UnixSocketTransportChannelProperties properties;
    private final P2PTransport transport;
    private final ExtendedActorSystem system;
    private ServerBootstrap serverBootstrap;
    private EpollEventLoopGroup bossGroup;
    private EpollEventLoopGroup workerGroup;
    private CompletableFuture<Void> shutdownFuture;
    private File sharedSocketDir;

    UnixSocketP2PTransportChannel(P2PTransport transport,
                                  UnixSocketTransportChannelProperties properties,
                                  ExtendedActorSystem system) {
        super();
        this.transport = transport;
        this.system = system;
        this.shutdownFuture = new CompletableFuture<>();
        this.properties = properties;

        bossGroup = new EpollEventLoopGroup(1);
        workerGroup = new EpollEventLoopGroup();

        bootstrap = new Bootstrap()
                .group(workerGroup)
                .channel(EpollDomainSocketChannel.class)
                .handler(new LoggingHandler(LogLevel.DEBUG))
                .handler(new DirectChannelInitializer(
                        system.name(), transport,
                        new EnvelopeMessageHandler(system, transport.getProvider(), transport.defaultAddress()),
                        this,
                        true));

        serverBootstrap = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(EpollServerDomainSocketChannel.class)
                .handler(new LoggingHandler(LogLevel.INFO))
                .childHandler(new DirectChannelInitializer(
                        system.name(), transport,
                        new EnvelopeMessageHandler(system, transport.getProvider(), transport.defaultAddress()),
                        this,
                        false));

    }

    // constructor call in P2PTransport::new from class reflection via className in config
    @SuppressWarnings("unused")
    public UnixSocketP2PTransportChannel(String system,
                                         Config config,
                                         P2PTransport transport,
                                         ExtendedActorSystem actorSystem) {
        this(transport, new UnixSocketTransportChannelProperties(config), actorSystem);
    }

    @Override
    public synchronized CompletableFuture<Void> start() {


        // start listening for peers
        File systemSockFile = Path.of(properties.getSharedDir(), system.name() + ".sock").toFile();
        sharedSocketDir = systemSockFile.getParentFile();
        if (!(sharedSocketDir.isDirectory() || sharedSocketDir.mkdirs())) {
            return CompletableFuture.failedFuture(new IllegalStateException("Failed to get shared directory!"));
        }

        if (!systemSockFile.isFile() || systemSockFile.delete()) {
            serverBootstrap.bind(new DomainSocketAddress(systemSockFile));
        } else {
            return CompletableFuture.failedFuture(
                    new P2PTransportChannelException("Failed to bind to file! Unable to remove previous socket file: "
                            + systemSockFile.getAbsolutePath()));
        }
        log.debug("AkkaHostP2PTransportChannel now listens on file '{}'.", systemSockFile.getAbsolutePath());

        // channel can receive after start but not send
        // after channel calls this::addPeer -> sending will be possible
        // TODO peer connections should be retried on failure (adjusting RetryStrategy for peer connections)
        return CompletableFuture.allOf(
                listSystemNames().stream()
                        .filter(systemName -> !Objects.equals(systemName, system.name()))
                        .map(systemName -> new DomainSocketAddress(Path.of(properties.getSharedDir(), systemName).toFile()))
                        .map(this::connectTo)
                        .map(cf -> CompletableFuture.runAsync(() -> cf.syncUninterruptibly().channel()))
                        .toArray(CompletableFuture<?>[]::new)
        );
    }

    private List<String> listSystemNames() {
        return ofNullable(sharedSocketDir.listFiles())
                .map(files -> Arrays.stream(files)
                        .map(File::getName)
                        .collect(Collectors.toList()))
                .orElse(Lists.newArrayList());
    }


    @Override
    public CompletableFuture<Void> closeFuture() {
        return shutdownFuture;
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
