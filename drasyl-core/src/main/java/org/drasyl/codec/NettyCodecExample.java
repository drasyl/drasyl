/*
 * Copyright (c) 2020-2021 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.codec;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import org.drasyl.DrasylConfig;
import org.drasyl.identity.IdentityManager;

import java.io.IOException;

public class NettyCodecExample {
    public static void main(final String[] args) throws InterruptedException, IOException {
//        final Logger root = Logger.getLogger("");
//        root.setLevel(Level.ALL);
//        for (final Handler handler : root.getHandlers()) {
//            handler.setLevel(Level.ALL);
//        }
//        System.out.println("level set: " + Level.ALL.getName());

        // new DrasylNode()
        final IdentityManager identityManager = new IdentityManager(DrasylConfig.of());
        identityManager.loadOrCreateIdentity();
        final DrasylBootstrap bootstrap = new DrasylBootstrap(event -> {
            System.err.println("event = " + event);
        }, identityManager.getIdentity())
                .config(DrasylConfig.of())
                // TODO: idleTimeout(60 seconds)
//                .handler(new SimpleChannelInboundHandler<Object>() {
//                    @Override
//                    protected void channelRead0(final ChannelHandlerContext ctx,
//                                                final Object msg) {
//                        // DrasylNode#onEvent()
//                    }
//                })
                ;

        // DrasylNode#start

        final ChannelFuture future = bootstrap.bind();
        future.awaitUninterruptibly();

        final Channel channel = future.channel();

//        Thread.sleep(5_000);
//
//        // DrasylNode#send()
//        // wirft UnsupportedOperationException, müssen an ein child channel ran
//        final ChannelPromise promise = channel.newPromise();
//        channel.writeAndFlush("Hallo welt", promise); // empfänger fehlt
//
//        Thread.sleep(5_000);
//
//        // DrasylNode#shutdown
//        channel.close();
    }
}
