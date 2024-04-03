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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.drasyl.handler.PeerReportedInetAddressHandlerEmitter.PeerReportedInetAddress;
import org.drasyl.identity.DrasylAddress;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

/**
 * Uses emitted {@link PeerReportedInetAddress} events to detect the applied mapping policy the NAT
 * uses we are located behind.
 */
public class NatMappingPolicyDetector extends ChannelInboundHandlerAdapter {
    private final Map<DrasylAddress, InetSocketAddress> reportedAddresses = new HashMap<>();

    @Override
    public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) {
        if (evt instanceof PeerReportedInetAddress) {
            reportedAddresses.put(((PeerReportedInetAddress) evt).peerAddress(), ((PeerReportedInetAddress) evt).reportedAddress());

            if (reportedAddresses.size() > 1) {
                System.out.println("ctx = " + ctx + ", evt = " + evt);
            }
        }

        ctx.fireUserEventTriggered(evt);
    }
}
