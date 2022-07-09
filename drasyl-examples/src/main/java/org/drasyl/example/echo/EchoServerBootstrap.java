package org.drasyl.example.echo;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import org.drasyl.channel.DrasylChannel;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.handler.remote.ApplicationMessageToPayloadCodec;
import org.drasyl.handler.remote.ByteToRemoteMessageCodec;
import org.drasyl.handler.remote.InvalidProofOfWorkFilter;
import org.drasyl.handler.remote.OtherNetworkFilter;
import org.drasyl.handler.remote.UdpServer;
import org.drasyl.handler.remote.crypto.ProtocolArmHandler;
import org.drasyl.handler.remote.internet.TraversingInternetDiscoveryChildrenHandler;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.node.identity.IdentityManager;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Same like {@link EchoServerNode}, but uses the more extensive
 * {@link io.netty.bootstrap.ServerBootstrap} interface.
 *
 * @see EchoClient
 * @see EchoServerNode
 */
public class EchoServerBootstrap {
    private static final String IDENTITY = System.getProperty("identity", "echo-server.identity");
    private static final int NETWORK_ID = 1;

    public static void main(final String[] args) throws IOException {
        // load/create identity
        final File identityFile = new File(IDENTITY);
        if (!identityFile.exists()) {
            IdentityManager.writeIdentityFile(identityFile.toPath(), Identity.generateIdentity());
        }
        final Identity identity = IdentityManager.readIdentityFile(identityFile.toPath());

        final EventLoopGroup group = new NioEventLoopGroup();
        final ServerBootstrap b = new ServerBootstrap()
                .group(group)
                .channel(DrasylServerChannel.class)
                .handler(new ChannelInitializer<DrasylServerChannel>() {
                    @Override
                    protected void initChannel(final DrasylServerChannel ch) {
                        // control plane channel
                        final ChannelPipeline p = ch.pipeline();

                        p.addLast(new UdpServer(22527));
                        p.addLast(new ByteToRemoteMessageCodec());
                        p.addLast(new OtherNetworkFilter(NETWORK_ID));
                        p.addLast(new InvalidProofOfWorkFilter());
                        p.addLast(new ProtocolArmHandler(identity, 100));
                        p.addLast(new TraversingInternetDiscoveryChildrenHandler(NETWORK_ID, identity.getIdentityPublicKey(), identity.getIdentitySecretKey(), identity.getProofOfWork(), 0, 5_000, 30_000, 60_000, Map.of(
                                IdentityPublicKey.of("c0900bcfabc493d062ecd293265f571edb70b85313ba4cdda96c9f77163ba62d"), new InetSocketAddress("sp-fra1.drasyl.org", 22527),
                                IdentityPublicKey.of("5b4578909bf0ad3565bb5faf843a9f68b325dd87451f6cb747e49d82f6ce5f4c"), new InetSocketAddress("sp-nbg2.drasyl.org", 22527)
                        ), 60_000, 100));
                        p.addLast(new ApplicationMessageToPayloadCodec(NETWORK_ID, identity.getIdentityPublicKey(), identity.getProofOfWork()));

                        System.out.println("EchoServer listening on address " + identity.getAddress());
                    }
                })
                .childHandler(new ChannelInitializer<DrasylChannel>() {
                    @Override
                    protected void initChannel(final DrasylChannel ch) {
                        // data plane channel
                        final ChannelPipeline p = ch.pipeline();

                        p.addLast(new SimpleChannelInboundHandler<ByteBuf>() {
                            @Override
                            protected void channelRead0(final ChannelHandlerContext ctx,
                                                        final ByteBuf msg) {
                                System.out.println("Got `" + msg.toString(UTF_8) + "` from `" + ctx.channel().remoteAddress() + "`");
                                ctx.writeAndFlush(msg.resetReaderIndex().retain()).addListener((ChannelFutureListener) f -> {
                                    if (!f.isSuccess()) {
                                        System.err.println("Unable to process message:");
                                        f.cause().printStackTrace(System.err);
                                    }
                                });
                            }
                        });
                    }
                });

        try {
            final Channel ch = b.bind(identity.getAddress()).syncUninterruptibly().channel();
            ch.closeFuture().awaitUninterruptibly();
        }
        finally {
            group.shutdownGracefully();
        }
    }
}
