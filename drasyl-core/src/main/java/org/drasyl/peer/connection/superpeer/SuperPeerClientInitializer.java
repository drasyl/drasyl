package org.drasyl.peer.connection.superpeer;

import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.drasyl.DrasylNodeConfig;
import org.drasyl.peer.connection.AbstractClientInitializer;
import org.drasyl.peer.connection.handler.ExceptionHandler;
import org.drasyl.peer.connection.handler.QuitMessageHandler;
import org.drasyl.peer.connection.superpeer.handler.SuperPeerHandler;
import org.drasyl.peer.connection.superpeer.handler.WelcomeGuard;
import org.drasyl.util.WebsocketUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import java.net.URI;

import static org.drasyl.peer.connection.superpeer.handler.WelcomeGuard.WELCOME_GUARD;

/**
 * Creates a newly configured {@link ChannelPipeline} for a ClientConnection to a node server.
 */
@SuppressWarnings({ "java:S110" })
public class SuperPeerClientInitializer extends AbstractClientInitializer {
    private static final Logger LOG = LoggerFactory.getLogger(SuperPeerClientInitializer.class);
    private final DrasylNodeConfig config;
    private final SuperPeerClient superPeerClient;

    public SuperPeerClientInitializer(DrasylNodeConfig config,
                                      URI endpoint, SuperPeerClient superPeerClient) {
        super(config.getFlushBufferSize(), config.getSuperPeerIdleTimeout(),
                config.getSuperPeerIdleRetries(), endpoint);
        this.config = config;
        this.superPeerClient = superPeerClient;
    }

    @Override
    protected void customStage(ChannelPipeline pipeline) {
        // QuitMessage handler
        pipeline.addLast(QuitMessageHandler.QUIT_MESSAGE_HANDLER, QuitMessageHandler.INSTANCE);

        // Guards
        pipeline.addLast(WELCOME_GUARD, new WelcomeGuard(config.getSuperPeerPublicKey(), superPeerClient.getIdentityManager().getKeyPair().getPublicKey()));

        // Super peer handler
        pipeline.addLast(SuperPeerHandler.SUPER_PEER_HANDLER, new SuperPeerHandler(superPeerClient, target));
    }

    @Override
    protected void exceptionStage(ChannelPipeline pipeline) {
        // Catch Errors
        pipeline.addLast(ExceptionHandler.EXCEPTION_HANDLER, new ExceptionHandler());
    }

    @Override
    protected SslHandler generateSslContext(SocketChannel ch) {
        if (WebsocketUtil.isWebsocketSecureURI(target)) {
            try {
                SslContext sslContext = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE)
                        .protocols(config.getServerSSLProtocols()).build();
                return sslContext.newHandler(ch.alloc(), target.getHost(), WebsocketUtil.websocketPort(target));
            }
            catch (SSLException e) {
                LOG.error("SSLException: ", e);
            }
        }
        return null;
    }
}
