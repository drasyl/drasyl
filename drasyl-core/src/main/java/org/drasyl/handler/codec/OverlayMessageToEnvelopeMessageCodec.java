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
package org.drasyl.handler.codec;

import io.netty.channel.AddressedEnvelope;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.DefaultAddressedEnvelope;
import io.netty.handler.codec.MessageToMessageCodec;
import org.drasyl.channel.OverlayAddressedMessage;
import org.drasyl.identity.DrasylAddress;

import java.util.List;

/**
 * Encodes {@link OverlayAddressedMessage}s to {@link AddressedEnvelope} messages and vice versa.
 */
@Sharable
public class OverlayMessageToEnvelopeMessageCodec extends MessageToMessageCodec<OverlayAddressedMessage<?>, AddressedEnvelope<?, ?>> {
    @Override
    protected void encode(ChannelHandlerContext ctx,
                          AddressedEnvelope<?, ?> msg,
                          List<Object> out) {
        out.add(new OverlayAddressedMessage<>(msg.content(), (DrasylAddress) msg.recipient(), (DrasylAddress) msg.sender()).retain());
    }

    @Override
    protected void decode(ChannelHandlerContext ctx,
                          OverlayAddressedMessage<?> msg,
                          List<Object> out) {
        out.add(new DefaultAddressedEnvelope<>(msg.content(), msg.recipient(), msg.sender()).retain());
    }
}
