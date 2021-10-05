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
package org.drasyl.handler.remote.crypto;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import org.drasyl.channel.AddressedMessage;
import org.drasyl.handler.remote.protocol.UnarmedProtocolMessage;

import java.util.List;

public class UnarmedMessageDecoder extends MessageToMessageDecoder<AddressedMessage<UnarmedProtocolMessage, ?>> {
    @Override
    public boolean acceptInboundMessage(final Object msg) {
        return msg instanceof AddressedMessage && ((AddressedMessage<?, ?>) msg).message() instanceof UnarmedProtocolMessage;
    }

    @Override
    protected void decode(final ChannelHandlerContext ctx,
                          final AddressedMessage<UnarmedProtocolMessage, ?> msg,
                          final List<Object> out) throws Exception {
        out.add(msg.replace(msg.message().retain().read()));
    }
}
