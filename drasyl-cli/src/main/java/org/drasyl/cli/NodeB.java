/*
 * Copyright (c) 2020-2022 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.cli;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import org.drasyl.channel.DrasylChannel;
import org.drasyl.handler.connection.ConnectionSynchronizationCodec;
import org.drasyl.handler.connection.ConnectionSynchronizationHandler;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.node.DrasylConfig;
import org.drasyl.node.DrasylException;
import org.drasyl.node.DrasylNode;
import org.drasyl.node.channel.DrasylNodeChannelInitializer;
import org.drasyl.node.event.Event;
import org.drasyl.node.event.NodeOnlineEvent;
import org.drasyl.node.identity.IdentityManager;

import java.io.IOException;
import java.nio.file.Path;

public class NodeB extends DrasylNode {
    protected NodeB(final DrasylConfig config) throws DrasylException {
        super(config);
        bootstrap.childHandler(new DrasylNodeChannelInitializer(config, this) {
            @Override
            protected void initChannel(final DrasylChannel ch) throws Exception {
                final DrasylAddress nodeA = IdentityManager.readIdentityFile(Path.of("nodeA.identity")).getAddress();
                if (ch.remoteAddress().equals(nodeA)) {
                    final ChannelPipeline p = ch.pipeline();
                    p.addLast(new ConnectionSynchronizationCodec());
                    p.addLast(new ConnectionSynchronizationHandler(true));
                    p.addLast(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void userEventTriggered(final ChannelHandlerContext ctx,
                                                       final Object evt) throws Exception {
                            if (evt instanceof ConnectionSynchronizationHandler.ConnectionSynchronized) {
                                System.err.println(evt);
                            }
                            super.userEventTriggered(ctx, evt);
                        }
                    });
                }
            }
        });
    }

    @Override
    public void onEvent(final Event event) {
        System.out.println(event);
        if (event instanceof NodeOnlineEvent) {
            System.out.println("geht los");
            try {
                final DrasylAddress nodeA = IdentityManager.readIdentityFile(Path.of("nodeA.identity")).getAddress();
                send(nodeA, ConnectionSynchronizationHandler.UserCall.OPEN);
//                DrasylNodeSharedEventLoopGroupHolder.getChildGroup().scheduleAtFixedRate(() -> {
//                    send(nodeA, Unpooled.copiedBuffer("Huhu", UTF_8));
//                }, 0L, 5L, SECONDS);
            }
            catch (final IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void main(final String[] args) throws DrasylException, InterruptedException {
        final NodeB node = new NodeB(DrasylConfig.newBuilder(DrasylConfig.of()).identityPath(Path.of("nodeB.identity"))
//                .remoteLocalHostDiscoveryEnabled(false)
//                .remoteLocalNetworkDiscoveryEnabled(false)
                .build());
        node.start();
    }
}
