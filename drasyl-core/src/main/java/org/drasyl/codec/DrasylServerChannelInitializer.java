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
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import org.drasyl.DrasylAddress;

class DrasylServerChannelInitializer extends ChannelInitializer<Channel> {
    @Override
    protected void initChannel(final Channel ch) {
        ch.pipeline().addFirst(new SimpleChannelInboundHandler<AddressedFullReadMessage>() {
            @Override
            protected void channelRead0(ChannelHandlerContext ctx, AddressedFullReadMessage msg) throws Exception {
                 ctx.fireChannelRead(new DrasylChannel(ctx.channel(), (DrasylAddress) msg.sender()));
            }
        });
        ch.pipeline().addFirst(new InternetDiscovery(((DrasylServerChannel) ch).drasylConfig()));
        ch.pipeline().addFirst(new ArmHandler(
                ((DrasylServerChannel) ch).drasylConfig().getRemoteMessageArmSessionMaxCount(),
                ((DrasylServerChannel) ch).drasylConfig().getRemoteMessageArmSessionMaxAgreements(),
                ((DrasylServerChannel) ch).drasylConfig().getRemoteMessageArmSessionExpireAfter(),
                ((DrasylServerChannel) ch).drasylConfig().getRemoteMessageArmSessionRetryInterval()));
        ch.pipeline().addFirst(RemoteMessageToByteBufCodec.INSTANCE);
        ch.pipeline().addFirst(new UdpServer());
    }
}
