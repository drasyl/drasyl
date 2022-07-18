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
import org.drasyl.channel.TraversingDrasylServerChannelInitializer;
import org.drasyl.identity.Identity;
import org.drasyl.node.identity.IdentityManager;

import java.io.File;
import java.io.IOException;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Same like {@link EchoServerNode}, but uses the more extensive
 * {@link io.netty.bootstrap.ServerBootstrap} interface.
 *
 * @see EchoClient
 * @see EchoServerNode
 */
@SuppressWarnings("java:S106")
public class EchoServerBootstrap {
    private static final String IDENTITY = System.getProperty("identity", "echo-server.identity");

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
                .handler(new TraversingDrasylServerChannelInitializer(identity))
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
            System.out.println("EchoServer listening on address " + ch.localAddress());
            ch.closeFuture().awaitUninterruptibly();
        }
        finally {
            group.shutdownGracefully();
        }
    }
}