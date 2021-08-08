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
package org.drasyl.pipeline.skeleton;

import io.netty.channel.ChannelHandlerContext;
import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.DrasylConfig;
import org.drasyl.channel.EmbeddedDrasylServerChannel;
import org.drasyl.channel.MigrationOutboundMessage;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.address.Address;
import org.drasyl.util.FutureCombiner;
import org.drasyl.util.FutureUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;

import static org.mockito.Answers.RETURNS_DEEP_STUBS;

@ExtendWith(MockitoExtension.class)
class SimpleOutboundHandlerTest {
    @Mock
    private Identity identity;
    @Mock
    private PeersManager peersManager;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private DrasylConfig config;

    @Test
    void shouldTriggerOnMatchedMessage(@Mock final Address recipient) {
        final SimpleOutboundHandler<byte[], Address> handler = new SimpleOutboundHandler<>() {
            @Override
            protected void matchedOutbound(final ChannelHandlerContext ctx,
                                           final Address recipient,
                                           final byte[] msg,
                                           final CompletableFuture<Void> future) {
                FutureCombiner.getInstance().add(FutureUtil.toFuture(ctx.writeAndFlush(new MigrationOutboundMessage<>((Object) new String(msg), recipient)))).combine(future);
            }
        };

        final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager, handler);
        try {
            final TestObserver<String> outboundMessageTestObserver = pipeline.drasylOutboundMessages(String.class).test();

            pipeline.pipeline().writeAndFlush(new MigrationOutboundMessage<>((Object) "Hallo Welt".getBytes(), recipient));

            outboundMessageTestObserver.awaitCount(1)
                    .assertValueCount(1)
                    .assertValue("Hallo Welt");
        }
        finally {
            pipeline.drasylClose();
        }
    }

    @Test
    void shouldPassthroughsNotMatchingMessage(@Mock final IdentityPublicKey recipient) {
        final SimpleOutboundHandler<byte[], Address> handler = new SimpleOutboundHandler<>() {
            @Override
            protected void matchedOutbound(final ChannelHandlerContext ctx,
                                           final Address recipient,
                                           final byte[] msg,
                                           final CompletableFuture<Void> future) {
                FutureCombiner.getInstance().add(FutureUtil.toFuture(ctx.writeAndFlush(new MigrationOutboundMessage<>((Object) new String(msg), recipient)))).combine(future);
            }
        };

        final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager, handler);
        try {
            final TestObserver<String> outboundMessageTestObserver = pipeline.drasylOutboundMessages(String.class).test();

            pipeline.pipeline().writeAndFlush(new MigrationOutboundMessage<>((Object) 1337, (Address) recipient));

            outboundMessageTestObserver.assertNoValues();
        }
        finally {
            pipeline.drasylClose();
        }
    }
}
