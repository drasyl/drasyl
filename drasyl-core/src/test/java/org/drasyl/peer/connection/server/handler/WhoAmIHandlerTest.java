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
package org.drasyl.peer.connection.server.handler;

import io.netty.channel.embedded.EmbeddedChannel;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.peer.connection.message.IamMessage;
import org.drasyl.peer.connection.message.WhoAreYouMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@ExtendWith(MockitoExtension.class)
class WhoAmIHandlerTest {
    @Mock
    private CompressedPublicKey mockedPublicKey;

    @Test
    void shouldSendIamMessageOnWhoAreYouMessage() {
        WhoAmIHandler handler = new WhoAmIHandler(mockedPublicKey);
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        WhoAreYouMessage msg = new WhoAreYouMessage();
        assertFalse(channel.writeInbound(msg));

        assertEquals(new IamMessage(mockedPublicKey, msg.getId()), channel.readOutbound());
    }
}