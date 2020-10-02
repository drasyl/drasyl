/*
 * Copyright (c) 2020.
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

package org.drasyl.peer.connection.handler;

import io.netty.channel.embedded.EmbeddedChannel;
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RelayableMessageGuardTest {
    @Mock
    private ApplicationMessage applicationMessage;

    @Test
    void shouldPassMessagesThatHaveNotReachedTheirHopCountLimitAndIncrementHopCount() {
        when(applicationMessage.getHopCount()).thenReturn((short) 1);

        final RelayableMessageGuard handler = new RelayableMessageGuard((short) 2);
        final EmbeddedChannel channel = new EmbeddedChannel(handler);

        channel.writeOutbound(applicationMessage);
        channel.flush();

        verify(applicationMessage).incrementHopCount();
        assertEquals(applicationMessage, channel.readOutbound());
    }

    @Test
    void shouldDiscardMessagesThatHaveReachedTheirHopCountLimit() {
        when(applicationMessage.getHopCount()).thenReturn((short) 1);

        final RelayableMessageGuard handler = new RelayableMessageGuard((short) 1);
        final EmbeddedChannel channel = new EmbeddedChannel(handler);

        channel.writeOutbound(applicationMessage);
        channel.flush();

        assertNull(channel.readOutbound());
    }
}