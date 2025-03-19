/*
 * Copyright (c) 2020-2025 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.channel;

import io.netty.channel.ChannelPromise;
import io.netty.util.concurrent.ScheduledFuture;
import org.drasyl.channel.JavaDrasylServerChannel.State;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.Identity;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JavaDrasylServerChannelTest {
    @Nested
    class DoBind {
        @Test
        void shouldSetLocalAddressAndActivateChannel(@Mock(answer = RETURNS_DEEP_STUBS) final Identity localAddress,
                                                     @Mock(answer = RETURNS_DEEP_STUBS) final ChannelPromise activePromise,
                                                     @Mock(answer = RETURNS_DEEP_STUBS) final ScheduledFuture<?> cachedTimeTask) {
            final JavaDrasylServerChannel channel = new JavaDrasylServerChannel(State.OPEN, Map.of(), null, null, activePromise, cachedTimeTask);

            channel.doBind(localAddress);

            assertTrue(channel.isActive());
            assertEquals(localAddress.getAddress(), channel.localAddress0());
        }

        @Test
        void shouldRejectNonIdentity(@Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress localAddress,
                                     @Mock(answer = RETURNS_DEEP_STUBS) final ScheduledFuture<?> cachedTimeTask) {
            final JavaDrasylServerChannel channel = new JavaDrasylServerChannel(State.OPEN, Map.of(), null, null, null, cachedTimeTask);

            assertThrows(IllegalArgumentException.class, () -> channel.doBind(localAddress));
        }
    }

    @Nested
    class DoClose {
        @Test
        void shouldRemoveLocalAddressAndCloseChannel(@Mock(answer = RETURNS_DEEP_STUBS) final Identity identity,
                                                     @Mock(answer = RETURNS_DEEP_STUBS) final ScheduledFuture<?> cachedTimeTask) {
            final JavaDrasylServerChannel channel = new JavaDrasylServerChannel(State.OPEN, Map.of(), identity, null, null, cachedTimeTask);

            channel.doClose();

            assertNull(channel.localAddress0());
            assertFalse(channel.isOpen());
            assertFalse(channel.isActive());
            verify(cachedTimeTask).cancel(false);
        }
    }
}
