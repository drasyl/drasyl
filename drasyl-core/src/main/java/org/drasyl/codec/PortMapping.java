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

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.pipeline.address.InetSocketAddressWrapper;

/**
 * Represents a method for creating port forwarding (e.g., PCP, NAT-PMP, or UPnP-IGD).
 */
public interface PortMapping {
    /**
     * Tells the method to create a port forwarding and renew it independently. If no forwarding can
     * be created, {@code onFailure} must be called once.
     *
     * @param ctx       the handler context
     * @param event     the node up event
     * @param onFailure will be called once on failure
     */
    void start(ChannelHandlerContext ctx, NodeUpEvent event, Runnable onFailure);

    /**
     * Shall remove any existing port forwarding again.
     *
     * @param ctx the handler context
     */
    void stop(ChannelHandlerContext ctx);

    /**
     * Is called for incoming messages and thus enables this method to react to relevant messages.
     *
     * @param ctx    the handler context
     * @param sender the sender of the message
     * @param msg    the message
     */
    void handleMessage(ChannelHandlerContext ctx,
                       InetSocketAddressWrapper sender,
                       ByteBuf msg);

    /**
     * Is called for incoming messages and returns true if the message should be consumed and
     * removed from the pipeline.
     *
     * @param sender the sender of the message
     * @param msg    the message
     * @return {@code true} if the message is relevant for the current port forwarding method.
     * Otherwise {@code false}
     */
    boolean acceptMessage(InetSocketAddressWrapper sender,
                          ByteBuf msg);
}
