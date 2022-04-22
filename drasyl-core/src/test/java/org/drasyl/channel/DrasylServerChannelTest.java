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
package org.drasyl.channel;

import org.drasyl.channel.DrasylServerChannel.State;
import org.drasyl.identity.DrasylAddress;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.SocketAddress;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DrasylServerChannelTest {
    @Nested
    class DoBind {
        @Test
        void shouldSetLocalAddressAndActivateChannel(@Mock final DrasylAddress localAddress) {
            final DrasylServerChannel channel = new DrasylServerChannel(State.OPEN, Map.of(), null);

            channel.doBind(localAddress);

            assertTrue(channel.isActive());
            assertEquals(localAddress, channel.localAddress0());
        }

        @Test
        void shouldRejectNonIdentity(@Mock final SocketAddress localAddress) {
            final DrasylServerChannel channel = new DrasylServerChannel(State.OPEN, Map.of(), null);

            assertThrows(IllegalArgumentException.class, () -> channel.doBind(localAddress));
        }
    }

    @Nested
    class DoClose {
        @Test
        void shouldRemoveLocalAddressAndCloseChannel(@Mock final DrasylAddress localAddress) {
            final DrasylServerChannel channel = new DrasylServerChannel(State.OPEN, Map.of(), localAddress);

            channel.doClose();

            assertNull(channel.localAddress0());
            assertFalse(channel.isOpen());
            assertFalse(channel.isActive());
        }
    }
}
