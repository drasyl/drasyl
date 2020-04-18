package city.sane.akka.p2p.transport.direct;

import akka.actor.ActorPath;
import akka.actor.ActorRef;
import akka.actor.Address;
import akka.actor.ExtendedActorSystem;
import city.sane.akka.p2p.P2PActorRef;
import city.sane.akka.p2p.P2PActorRefProvider;
import city.sane.akka.p2p.transport.InboundMessageEnvelope;
import city.sane.akka.p2p.transport.OutboundMessageEnvelope;
import city.sane.akka.p2p.transport.P2PTransport;
import city.sane.akka.p2p.transport.direct.handler.DirectChannelInitializer;
import city.sane.akka.p2p.transport.direct.messages.AkkaMessage;
import city.sane.akka.p2p.transport.direct.messages.SystemNameMessage;
import city.sane.akka.p2p.transport.handler.EnvelopeMessageHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mock;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

@Ignore
public class DirectChannelInitializerTest {

    @Mock
    private ExtendedActorSystem system;

    @Mock
    private P2PTransport transport;

    @Mock
    private EnvelopeMessageHandler messageHandler;

    @Mock
    private BundledDirectP2PTransportChannel parentChannel;
    private Bootstrap bootstrap;
    private ServerBootstrap serverBootstrap;

    private int port = 11111;

    @Before
    public void setUp() throws InterruptedException {
        initMocks(this);

        when(system.name()).thenReturn("mockedSystem1");

        serverBootstrap = new ServerBootstrap()
                .group(new NioEventLoopGroup())
                .channel(NioServerSocketChannel.class)
                .childHandler(new DirectChannelInitializer(
                        system.name(), transport,
                        new EnvelopeMessageHandlerStub(system, transport.getProvider(), transport.defaultAddress()),
                        parentChannel,
                        false))
                .option(ChannelOption.SO_BACKLOG, 128)
                .childOption(ChannelOption.SO_KEEPALIVE, true);

        bootstrap = new Bootstrap()
                .group(new NioEventLoopGroup())
                .channel(NioSocketChannel.class)
                .handler(new LoggingHandler(LogLevel.DEBUG))
                .handler(new DirectChannelInitializer(
                        system.name(), transport,
                        new EnvelopeMessageHandlerStub(system, transport.getProvider(), transport.defaultAddress()),
                        parentChannel,
                        true));

        serverBootstrap.bind(port).sync();
    }

    @Test
    public void directConnection() throws ExecutionException, InterruptedException {
        ChannelFuture channelFuture = bootstrap.connect("localhost", port);

        Channel channel = channelFuture.syncUninterruptibly().channel();

        verify(parentChannel, timeout(2000).times(2)).addPeer(any(), any());

        channel.close().sync();

        verify(parentChannel, timeout(2000).times(2)).removePeer(any());

    }

    class EnvelopeMessageHandlerStub extends EnvelopeMessageHandler {

        public EnvelopeMessageHandlerStub(ExtendedActorSystem system, P2PActorRefProvider provider, Address defaultAddress) {
            super(system, provider, defaultAddress);
        }

        @Override
        protected void channelWrite0(ChannelHandlerContext ctx, OutboundMessageEnvelope messageEnvelope) {
            ObjectMapper mapper = new ObjectMapper();
            String json = "";
            try {
                json = mapper.writeValueAsString(messageEnvelope.getMessage());
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }

            // send message
            ctx.writeAndFlush(new AkkaMessage(
                    json.getBytes(StandardCharsets.UTF_8), system.name(), messageEnvelope.getRecipient().path().address().system()));
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, AkkaMessage akkaMessage) {
            ObjectMapper mapper = new ObjectMapper();
            String jsonString = new String(akkaMessage.getBlob(), StandardCharsets.UTF_8);
            SystemNameMessage deserializedMessage = null;
            try {
                deserializedMessage = mapper.readValue(jsonString, SystemNameMessage.class);
            } catch (IOException e) {
                e.printStackTrace();
            }
            P2PActorRef akkaRecipient = new P2PActorRef(transport, ActorPath.fromString("bud://" + akkaMessage.getRecipientSystem() + "/user/test"));
            Address akkaRecipientAddress = akkaRecipient.path().address();
            InboundMessageEnvelope messageEnvelope = new InboundMessageEnvelope(deserializedMessage, ActorRef.noSender(), akkaRecipient, akkaRecipientAddress);
            ctx.fireChannelRead(messageEnvelope);
        }
    }
}
