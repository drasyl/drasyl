/*
 * Copyright (c) 2020-2025 Heiko Bornholdt and Kevin Röbert
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.drasyl.example.connection;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoopGroup;
import org.drasyl.channel.DrasylChannel;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.channel.rs.RustDrasylServerChannel;
import org.drasyl.handler.connection.ConnectionAbortedDueToUserTimeoutException;
import org.drasyl.handler.connection.ConnectionClosing;
import org.drasyl.handler.connection.ConnectionConfig;
import org.drasyl.handler.connection.ConnectionHandler;
import org.drasyl.handler.connection.ConnectionHandshakeCompleted;
import org.drasyl.handler.connection.ConnectionResetException;
import org.drasyl.handler.connection.SegmentCodec;
import org.drasyl.identity.Identity;
import org.drasyl.node.identity.IdentityManager;
import org.drasyl.util.EventLoopGroupUtil;

import java.io.File;
import java.io.IOException;

import static io.netty.channel.ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE;
import static org.drasyl.channel.rs.RustDrasylServerChannelConfig.UDP_PORT;

/**
 * This node waits for connection handshake from other peers.
 */
@SuppressWarnings({
        "common-java:DuplicatedBlocks",
        "java:S106",
        "java:S110",
        "java:S1188",
        "java:S2096"
})
public class ConnectionServer {
    private static final String IDENTITY = System.getProperty("identity", "connection-server.identity");

    public static void main(final String[] args) throws IOException {
        // load/create identity
        final File identityFile = new File(IDENTITY);
        if (!identityFile.exists()) {
            System.out.println("No identity present. Generate new one. This may take a while ...");
            IdentityManager.writeIdentityFile(identityFile.toPath(), Identity.generateIdentity());
        }
        final Identity identity = IdentityManager.readIdentityFile(identityFile.toPath());

        System.out.println("My address = " + identity.getAddress());

        final EventLoopGroup group = new DefaultEventLoopGroup();
        final EventLoopGroup udpServerGroup = EventLoopGroupUtil.getBestEventLoopGroup(1);
        final ServerBootstrap b = new ServerBootstrap()
                .group(group)
                .channel(RustDrasylServerChannel.class)
                .option(UDP_PORT, 22527)
                .handler(new ChannelInitializer<DrasylServerChannel>() {
                    @Override
                    protected void initChannel(DrasylServerChannel ch) {

                    }
                })
                .childHandler(new ChannelInitializer<DrasylChannel>() {
                    @Override
                    protected void initChannel(final DrasylChannel ch) {
                        final ChannelPipeline p = ch.pipeline();

                        final ConnectionConfig config = ConnectionConfig.newBuilder()
                                .activeOpen(false)
                                .build();

                        p.addLast(new SegmentCodec());
                        p.addLast(new ConnectionHandler(1234, 0, config));
                        p.addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelInactive(final ChannelHandlerContext ctx) {
                                System.out.println("Connection to " + ctx.channel().remoteAddress() + " closed.");

                                ctx.fireChannelInactive();
                            }

                            @Override
                            public void userEventTriggered(final ChannelHandlerContext ctx,
                                                           final Object evt) {
                                if (evt instanceof ConnectionHandshakeCompleted) {
                                    // handshake succeeded
                                    System.out.println("Handshake with " + ctx.channel().remoteAddress() + " succeeded. Connection established.");

                                    // add your connection-related handler here
                                }
                                else if (evt instanceof ConnectionClosing && ((ConnectionClosing) evt).initatedByRemotePeer()) {
                                    // confirm close request
                                    System.out.println("Peer wants to close the connection. Confirm close request.");

                                    ctx.pipeline().close().addListener(FIRE_EXCEPTION_ON_FAILURE);
                                }
                                else {
                                    ctx.fireUserEventTriggered(evt);
                                }
                            }

                            @Override
                            public void exceptionCaught(final ChannelHandlerContext ctx,
                                                        final Throwable cause) {
                                if (cause instanceof ConnectionAbortedDueToUserTimeoutException) {
                                    // handshake failed
                                    System.out.println("Handshake failed.");
                                    cause.printStackTrace();
                                }
                                else if (cause instanceof ConnectionResetException) {
                                    // connection reset
                                    System.out.println("Peer has reset the connection. This can happen if the client has not been shut down properly, resulting in a half-open connection. Client should initiate a new connection now.");
                                }
                                else {
                                    ctx.fireExceptionCaught(cause);
                                }
                            }
                        });
                    }
                });

        try {
            final Channel ch = b.bind(identity).syncUninterruptibly().channel();
            Runtime.getRuntime().addShutdownHook(new Thread(ch::close));
            ch.closeFuture().awaitUninterruptibly();
        }
        finally {
            udpServerGroup.shutdownGracefully();
            group.shutdownGracefully();
        }
    }
}
