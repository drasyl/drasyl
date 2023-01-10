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
package org.drasyl.example.rtt;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.drasyl.handler.PeersRttHandler;
import org.drasyl.handler.PeersRttHandler.PeersRttReport;
import org.drasyl.node.DrasylException;
import org.drasyl.node.DrasylNode;
import org.drasyl.node.event.Event;
import org.drasyl.node.handler.PeersManagerHandler;

/**
 * This example starts a node that collects and prints RTT information for all peers.
 */
public class RttNode extends DrasylNode {
    protected RttNode() throws DrasylException {
    }

    public static void main(String[] args) throws DrasylException, InterruptedException {
        final RttNode node = new RttNode();
        node.start().toCompletableFuture().whenComplete((result, e) -> {
            // successfully started?
            if (e == null) {
                final String name = node.pipeline().context(PeersManagerHandler.class).name();

                // generate every 5 seconds RTT report
                node.pipeline().addBefore(name, null, new PeersRttHandler(5_000L));

                // print RTT reports to System.out
                node.pipeline().addBefore(name, null, new ChannelInboundHandlerAdapter() {
                    @Override
                    public void userEventTriggered(final ChannelHandlerContext ctx,
                                                   final Object evt) {
                        if (evt instanceof PeersRttReport) {
                            final PeersRttReport rttReport = (PeersRttReport) evt;
                            System.out.println(rttReport);
                        }

                        ctx.fireUserEventTriggered(evt);
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
        System.out.println("RttNode.onEvent " + event);
    }
}
