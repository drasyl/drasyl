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
package org.drasyl.handler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.drasyl.channel.InetAddressedMessage;
import org.drasyl.util.internal.UnstableApi;

import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

@UnstableApi
public class ChannelReadListener extends SimpleChannelInboundHandler<InetAddressedMessage<ByteBuf>> {
    private final Consumer<InetAddressedMessage<ByteBuf>> readListener;
    private final Runnable readCompleteListener;

    public ChannelReadListener(final Consumer<InetAddressedMessage<ByteBuf>> readListener,
                               final Runnable readCompleteListener) {
        super(false);
        this.readListener = requireNonNull(readListener);
        this.readCompleteListener = requireNonNull(readCompleteListener);
    }

    @Override
    public boolean acceptInboundMessage(final Object msg) throws Exception {
        return msg instanceof InetAddressedMessage<?> && ((InetAddressedMessage<?>) msg).content() instanceof ByteBuf;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx,
                                InetAddressedMessage<ByteBuf> msg) throws Exception {
        readListener.accept(msg);
    }

    @Override
    public void channelReadComplete(final ChannelHandlerContext ctx) {
        readCompleteListener.run();
        ctx.fireChannelReadComplete();
    }
}
