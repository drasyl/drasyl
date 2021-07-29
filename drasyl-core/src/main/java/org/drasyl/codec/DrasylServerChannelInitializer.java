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
import org.drasyl.event.Event;
import org.drasyl.event.Node;
import org.drasyl.event.NodeDownEvent;
import org.drasyl.event.NodeNormalTerminationEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

class DrasylServerChannelInitializer extends ChannelInitializer<Channel> {
    private final Consumer<Event> eventConsumer;

    public DrasylServerChannelInitializer(final Consumer<Event> eventConsumer) {
        this.eventConsumer = eventConsumer;
    }

    @Override
    protected void initChannel(final Channel ch) {
        ch.pipeline().addFirst(new SimpleChannelInboundHandler<AddressedObject>() {
            private Identity identity;
            private final Map<DrasylAddress, Channel> channels = new ConcurrentHashMap<>();

            @Override
            public void channelUnregistered(final ChannelHandlerContext ctx) throws Exception {
                eventConsumer.accept(NodeNormalTerminationEvent.of(Node.of(identity)));

                super.channelUnregistered(ctx);
            }

            @Override
            public void channelActive(final ChannelHandlerContext ctx) throws Exception {
                identity = ((DrasylServerChannel) ctx.channel()).localAddress0();

                eventConsumer.accept(NodeUpEvent.of(Node.of(identity)));

                super.channelActive(ctx);
            }

            @Override
            public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
                eventConsumer.accept(NodeDownEvent.of(Node.of(identity)));

                super.channelInactive(ctx);
            }

            @Override
            public void userEventTriggered(final ChannelHandlerContext ctx,
                                           final Object evt) throws Exception {
                eventConsumer.accept((Event) evt);

                super.userEventTriggered(ctx, evt);
            }

            @Override
            protected void channelRead0(final ChannelHandlerContext ctx,
                                        final AddressedObject addressedMsg) throws Exception {
                final Object msg = addressedMsg.content();
                final IdentityPublicKey sender = addressedMsg.sender();

                // create/get channel
                final Channel channel = channels.computeIfAbsent(sender, key -> {
                    final DrasylChannel channel1 = new DrasylChannel(ctx.channel(), sender);
                    channel1.closeFuture().addListener(future -> channels.remove(key));
                    ctx.fireChannelRead(channel1);
                    return channel1;
                });

                // pass message to channel
                channel.pipeline().fireChannelRead(msg);
            }
        });
        ch.pipeline().addFirst(MessageSerializer.INSTANCE);
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
