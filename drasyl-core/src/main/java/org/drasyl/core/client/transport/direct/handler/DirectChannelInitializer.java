package org.drasyl.core.client.transport.direct.handler;

import org.drasyl.core.client.transport.P2PTransport;
import org.drasyl.core.client.transport.direct.AbstractDirectP2PTransportChannel;
import org.drasyl.core.client.transport.handler.EnvelopeMessageHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.DuplexChannel;
import io.netty.handler.codec.serialization.ClassResolvers;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoder;

/**
 * A channel for direct connections to peers
 */
public class DirectChannelInitializer extends ChannelInitializer<DuplexChannel> {
    private final EnvelopeMessageHandler envelopeMessageHandler;
    private final AbstractDirectP2PTransportChannel parentChannel;
    private final String systemName;
    private final boolean isChannelInitiator;
    private final P2PTransport transport;

    public DirectChannelInitializer(String systemName,
                                    P2PTransport transport,
                                    EnvelopeMessageHandler envelopeMessageHandler,
                                    AbstractDirectP2PTransportChannel parentChannel,
                                    boolean isChannelInitiator) {
        this.transport = transport;
        this.parentChannel = parentChannel;
        this.envelopeMessageHandler = envelopeMessageHandler;
        this.systemName = systemName;
        this.isChannelInitiator = isChannelInitiator;

    }


    @Override
    protected void initChannel(DuplexChannel ch) {
        ch.pipeline().addLast(
                new ObjectEncoder(),
                new ObjectDecoder(ClassResolvers.cacheDisabled(null)),
                envelopeMessageHandler,
                new DirectPeerHandler(systemName, transport, parentChannel, isChannelInitiator)
        );
    }
}
