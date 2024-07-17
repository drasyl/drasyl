/*
 * Copyright (c) 2020-2024 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.example.diss;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.DefaultEventLoopGroup;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.channel.DefaultDrasylServerChannelInitializer;
import org.drasyl.identity.Identity;
import org.drasyl.node.identity.IdentityManager;

import java.io.IOException;
import java.nio.file.Path;

public class BootstrapExample {
    public static void main(final String[] args) throws IOException {
Identity i = ...;

ServerBootstrap b = new ServerBootstrap()
        .channel(DrasylServerChannel.class)
        .handler(new DefaultDrasylServerChannelInitializer())
        .childHandler(new ChannelInitializer<>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ch.pipeline().addLast(null);
            }
        }).group(new DefaultEventLoopGroup(1));
Channel ch = b.bind(i).syncUninterruptibly().channel();

        while (ch.isOpen()) {
            //System.out.printf("x");
        }
    }
}
