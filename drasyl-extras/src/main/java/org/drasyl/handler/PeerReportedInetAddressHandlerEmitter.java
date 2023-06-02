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
import io.netty.channel.SimpleChannelInboundHandler;
import org.drasyl.channel.InetAddressedMessage;
import org.drasyl.handler.remote.protocol.AcknowledgementMessage;
import org.drasyl.identity.DrasylAddress;

import java.net.InetSocketAddress;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * Emits a {@link PeerReportedInetAddress} every time we receive an {@link AcknowledgementMessage}
 * that tells us from which IP endpoint a peer is receiving our messages.
 */
public class PeerReportedInetAddressHandlerEmitter extends SimpleChannelInboundHandler<InetAddressedMessage<AcknowledgementMessage>> {
    public PeerReportedInetAddressHandlerEmitter() {
        super(false);
    }

    @Override
    public boolean acceptInboundMessage(final Object msg) {
        return msg instanceof InetAddressedMessage<?> && ((InetAddressedMessage<?>) msg).content() instanceof AcknowledgementMessage;
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx,
                                final InetAddressedMessage<AcknowledgementMessage> msg) {
        if (msg.content().getEndpoint() != null) {
            final PeerReportedInetAddress evt = new PeerReportedInetAddress(msg.content().getEndpoint(), msg.content().getSender(), msg.sender());
            ctx.fireUserEventTriggered(evt);
        }

        ctx.fireChannelRead(msg);
    }

    /**
     * Tells from which IP endpoint a peer is receiving our messages.
     */
    public static class PeerReportedInetAddress {
        private final InetSocketAddress reportedAddress;
        private final DrasylAddress peerAddress;
        private final InetSocketAddress peerInetAddress;

        public PeerReportedInetAddress(final InetSocketAddress reportedAddress,
                                       final DrasylAddress peerAddress,
                                       final InetSocketAddress peerInetAddress) {
            this.reportedAddress = requireNonNull(reportedAddress);
            this.peerAddress = requireNonNull(peerAddress);
            this.peerInetAddress = requireNonNull(peerInetAddress);
        }

        @Override
        public String toString() {
            return "PeerDiscoveredInetAddress{" +
                    "reportedAddress=" + reportedAddress +
                    ", peerAddress=" + peerAddress +
                    ", peerInetAddress=" + peerInetAddress +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            PeerReportedInetAddress that = (PeerReportedInetAddress) o;
            return Objects.equals(reportedAddress, that.reportedAddress) && Objects.equals(peerAddress, that.peerAddress) && Objects.equals(peerInetAddress, that.peerInetAddress);
        }

        @Override
        public int hashCode() {
            return Objects.hash(reportedAddress, peerAddress, peerInetAddress);
        }

        public InetSocketAddress reportedAddress() {
            return reportedAddress;
        }

        public DrasylAddress peerAddress() {
            return peerAddress;
        }

        public InetSocketAddress peerInetAddress() {
            return peerInetAddress;
        }
    }
}
