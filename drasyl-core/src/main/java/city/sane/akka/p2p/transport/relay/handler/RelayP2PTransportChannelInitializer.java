package city.sane.akka.p2p.transport.relay.handler;

import city.sane.akka.p2p.transport.InboundMessageEnvelope;
import city.sane.akka.p2p.transport.handler.EnvelopeMessageHandler;
import org.drasyl.core.common.handler.ClientInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import java.net.URI;
import java.time.Duration;

/**
 * Defines a channel, which deliver all messages between actors via a single relay server.
 */
@SuppressWarnings({"squid:S00107", "squid:MaximumInheritanceDepth"})
public class RelayP2PTransportChannelInitializer extends ClientInitializer {
    private static final Logger LOG = LoggerFactory.getLogger(RelayP2PTransportChannelInitializer.class);

    private final RelayMessageHandler relayMessageHandler;
    private final RelayJoinHandler joinHandlerSupplier;
    private final SimpleChannelInboundHandler<InboundMessageEnvelope> inboundHandler;
    private final EnvelopeMessageHandler envelopeMessageHandler;
    private RelayJoinHandler currentJoinHandler;
    private URI target;

    public RelayP2PTransportChannelInitializer(EnvelopeMessageHandler envelopeMessageHandler,
                                               RelayMessageHandler relayMessageHandler,
                                               RelayJoinHandler joinHandlerSupplier,
                                               SimpleChannelInboundHandler<InboundMessageEnvelope> inboundHandler,
                                               int flushBufferSize, Duration readIdleTimeout, int pingPongRetries,
                                               int maxContentLength, URI target) {
        super(flushBufferSize, readIdleTimeout, pingPongRetries, maxContentLength, target);
        this.envelopeMessageHandler = envelopeMessageHandler;
        this.relayMessageHandler = relayMessageHandler;
        this.inboundHandler = inboundHandler;
        this.joinHandlerSupplier = joinHandlerSupplier;
        this.currentJoinHandler = null;
        this.target = target;
    }

    @Override
    protected SslHandler generateSslContext(SocketChannel ch) {
        if(this.target.getScheme().equals("wss")) {
            try {
                SslContext sslCtx = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();
                return sslCtx.newHandler(ch.alloc(), target.getHost(), target.getPort());
            } catch (SSLException e) {
                LOG.error("SSLException: ", e);
            }
        }

        return null;
    }

    @Override
    protected void customStage(ChannelPipeline pipeline) {
        currentJoinHandler = joinHandlerSupplier.apply(this.waitUntilConnected());

        pipeline.addLast(new RelayRequestHandler())
                .addLast(currentJoinHandler)
                .addLast(relayMessageHandler)
                .addLast(envelopeMessageHandler)
                .addLast(inboundHandler);
    }

    public RelayJoinHandler getCurrentJoinHandler() {
        synchronized (joinHandlerSupplier) {
            return currentJoinHandler;
        }
    }
}
