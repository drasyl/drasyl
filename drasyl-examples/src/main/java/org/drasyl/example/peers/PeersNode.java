/*
 * Copyright (c) 2020-2023 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.example.peers;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.drasyl.handler.peers.PeersHandler;
import org.drasyl.node.DrasylException;
import org.drasyl.node.DrasylNode;
import org.drasyl.node.event.Event;
import org.drasyl.node.handler.PeersManagerHandler;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * This example starts a node that prints peer information periodically.
 */
public class PeersNode extends DrasylNode {
    protected PeersNode() throws DrasylException {
    }

    public static void main(String[] args) throws DrasylException, InterruptedException {
        final PeersNode node = new PeersNode();
        node.start().toCompletableFuture().whenComplete((result, e) -> {
            // successfully started?
            if (e == null) {
                final String name = node.pipeline().context(PeersManagerHandler.class).name();

                final PeersHandler peersHandler = new PeersHandler();
                node.pipeline().addBefore(name, null, peersHandler);

                // print peers reports to System.out every 5 seconds
                node.pipeline().addBefore(name, null, new ChannelInboundHandlerAdapter() {
                    @Override
                    public void handlerAdded(ChannelHandlerContext ctx) {
                        ctx.executor().scheduleAtFixedRate(() -> {
                            System.out.println(peersHandler.getPeers());
                        }, 0, 5_000L, MILLISECONDS);
                    }
                });
            }
        });

        while (true) {
            Thread.sleep(100);
        }
    }

    @Override
    public void onEvent(final Event event) {
        System.out.println("PeersNode.onEvent " + event);
    }
}
