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
package org.drasyl.handler.remote;

import io.netty.buffer.ByteBuf;
import io.netty.channel.AddressedEnvelope;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import org.drasyl.channel.DrasylServerChannelConfig;
import org.drasyl.channel.IdentityChannel;
import org.drasyl.channel.OverlayAddressedMessage;
import org.drasyl.handler.remote.protocol.ApplicationMessage;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.internal.UnstableApi;

import java.util.List;

/**
 * This codec converts {@link ApplicationMessage}s to the embedded payload and vice versa.
 */
@UnstableApi
public class ApplicationMessageToPayloadCodec extends MessageToMessageCodec<AddressedEnvelope<ApplicationMessage, ?>, OverlayAddressedMessage<ByteBuf>> {
    private Identity identity;

    ApplicationMessageToPayloadCodec(Identity identity) {
        this.identity = identity;
    }

    public ApplicationMessageToPayloadCodec() {
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        identity = ((IdentityChannel) ctx.channel()).identity();
        ctx.fireChannelActive();
    }

    @Override
    public boolean acceptOutboundMessage(final Object msg) {
        return msg instanceof OverlayAddressedMessage && ((OverlayAddressedMessage<?>) msg).content() instanceof ByteBuf;
    }

    @Override
    protected void encode(final ChannelHandlerContext ctx,
                          final OverlayAddressedMessage<ByteBuf> msg,
                          final List<Object> out) throws Exception {
        final ApplicationMessage wrappedMsg = ApplicationMessage.of(config(ctx).getNetworkId(), (IdentityPublicKey) msg.recipient(), identity.getIdentityPublicKey(), identity.getProofOfWork(), msg.content().retain());
        out.add(msg.replace(wrappedMsg));
    }

    @Override
    public boolean acceptInboundMessage(final Object msg) {
        return msg instanceof AddressedEnvelope && ((AddressedEnvelope<?, ?>) msg).content() instanceof ApplicationMessage;
    }

    @Override
    protected void decode(final ChannelHandlerContext ctx,
                          final AddressedEnvelope<ApplicationMessage, ?> msg,
                          final List<Object> out) {
        out.add(new OverlayAddressedMessage<>(msg.content().getPayload().retain(), msg.content().getRecipient(), msg.content().getSender()));
    }

    private static DrasylServerChannelConfig config(final ChannelHandlerContext ctx) {
        return (DrasylServerChannelConfig) ctx.channel().config();
    }
}
