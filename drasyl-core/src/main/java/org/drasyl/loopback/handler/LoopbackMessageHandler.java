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
package org.drasyl.loopback.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import org.drasyl.DrasylAddress;
import org.drasyl.channel.AddressedMessage;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * This handler converts outgoing messages addressed to the local node to incoming messages
 * addressed to the local node.
 */
public class LoopbackMessageHandler extends MessageToMessageEncoder<AddressedMessage<?, ?>> {
    private static final Logger LOG = LoggerFactory.getLogger(LoopbackMessageHandler.class);
    private final DrasylAddress myAddress;

    public LoopbackMessageHandler(final DrasylAddress myAddress) {
        this.myAddress = requireNonNull(myAddress);
    }

    @Override
    protected void encode(final ChannelHandlerContext ctx,
                          final AddressedMessage<?, ?> msg,
                          final List<Object> out) throws Exception {
        if (myAddress.equals(msg.address())) {
            LOG.trace("Outbound message `{}` is addressed to us. Convert to inbound message.", ((AddressedMessage<?, ?>) msg)::message);
            out.add(new AddressedMessage<>(msg.message(), myAddress));
        }
        else {
            // pass through message
            out.add(msg.retain());
        }
    }
}
