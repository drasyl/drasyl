/*
 * Copyright (c) 2020-2021.
 *
 * This file is part of drasyl.
 *
 *  drasyl is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  drasyl is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.drasyl.pipeline.skeleton;

import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.DrasylConfig;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.EmbeddedPipeline;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.HandlerMask;
import org.drasyl.pipeline.address.Address;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class SimpleOutboundHandlerTest {
    @Mock
    private Identity identity;
    @Mock
    private PeersManager peersManager;
    @Mock
    private DrasylConfig config;

    @Test
    void shouldTriggerOnMatchedMessage(@Mock final Address recipient) {
        final SimpleOutboundHandler<byte[], Address> handler = new SimpleOutboundHandler<>() {
            @Override
            protected void matchedWrite(final HandlerContext ctx,
                                        final Address recipient,
                                        final byte[] msg,
                                        final CompletableFuture<Void> future) {
                ctx.write(recipient, new String(msg), future);
            }
        };

        try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
            final TestObserver<String> outboundMessageTestObserver = pipeline.outboundMessages(String.class).test();

            pipeline.processOutbound(recipient, "Hallo Welt".getBytes());

            outboundMessageTestObserver.awaitCount(1)
                    .assertValueCount(1)
                    .assertValue("Hallo Welt");
        }
    }

    @Test
    void shouldPassthroughsNotMatchingMessage(@Mock final CompressedPublicKey recipient) {
        final SimpleOutboundHandler<byte[], Address> handler = new SimpleOutboundHandler<>() {
            @Override
            protected void matchedWrite(final HandlerContext ctx,
                                        final Address recipient,
                                        final byte[] msg,
                                        final CompletableFuture<Void> future) {
                ctx.write(recipient, new String(msg), future);
            }
        };

        try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
            final TestObserver<String> outboundMessageTestObserver = pipeline.outboundMessages(String.class).test();

            pipeline.processOutbound(recipient, 1337);

            outboundMessageTestObserver.assertNoValues();
        }
    }

    @Test
    void shouldReturnCorrectHandlerMask() {
        assertEquals(HandlerMask.WRITE_MASK, HandlerMask.mask(SimpleOutboundHandler.class));
    }
}
