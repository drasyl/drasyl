package org.drasyl.core.client.handler;

import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.drasyl.core.client.SuperPeerClient;
import org.drasyl.core.common.handler.ClientInitializer;
import org.drasyl.core.common.handler.ExceptionHandler;
import org.drasyl.core.common.handler.QuitMessageHandler;
import org.drasyl.core.node.DrasylNodeConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import java.net.URI;

import static org.drasyl.core.client.handler.SuperPeerHandler.SUPER_PEER_HANDLER;
import static org.drasyl.core.client.handler.WelcomeGuard.WELCOME_GUARD;
import static org.drasyl.core.common.handler.ExceptionHandler.EXCEPTION_HANDLER;
import static org.drasyl.core.common.handler.QuitMessageHandler.QUIT_MESSAGE_HANDLER;
import static org.drasyl.core.common.util.WebsocketUtil.isWebsocketSecureURI;
import static org.drasyl.core.common.util.WebsocketUtil.websocketPort;

/**
 * Creates a newly configured {@link ChannelPipeline} for a ClientConnection to a node server.
 */
@SuppressWarnings({ "java:S110" })
public class SuperPeerClientInitializer extends ClientInitializer {
    private static final Logger LOG = LoggerFactory.getLogger(SuperPeerClientInitializer.class);
    private final DrasylNodeConfig config;
    private final SuperPeerClient superPeerClient;

    public SuperPeerClientInitializer(DrasylNodeConfig config,
                                      URI endpoint, SuperPeerClient superPeerClient) {
        super(config.getFlushBufferSize(), config.getSuperPeerIdleTimeout(),
                config.getSuperPeerIdleRetries(), 65536, endpoint);
        this.config = config;
        this.superPeerClient = superPeerClient;
    }

    @Override
    protected SslHandler generateSslContext(SocketChannel ch) {
        if (isWebsocketSecureURI(target)) {
            try {
                SslContext sslContext = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE)
                        .protocols(config.getServerSSLProtocols()).build();
                return sslContext.newHandler(ch.alloc(), target.getHost(), websocketPort(target));
            }
            catch (SSLException e) {
                LOG.error("SSLException: ", e);
            }
        }
        return null;
    }

    @Override
    protected void customStage(ChannelPipeline pipeline) {
        // QuitMessage handler
        pipeline.addLast(QUIT_MESSAGE_HANDLER, QuitMessageHandler.INSTANCE);

        // Guards
        pipeline.addLast(WELCOME_GUARD, new WelcomeGuard(config.getSuperPeerPublicKey(), superPeerClient.getIdentityManager().getKeyPair().getPublicKey()));

        // Super peer handler
        pipeline.addLast(SUPER_PEER_HANDLER, new SuperPeerHandler(superPeerClient, target));
    }

    @Override
    protected void exceptionStage(ChannelPipeline pipeline) {
        // Catch Errors
        pipeline.addLast(EXCEPTION_HANDLER, new ExceptionHandler());
    }
}
