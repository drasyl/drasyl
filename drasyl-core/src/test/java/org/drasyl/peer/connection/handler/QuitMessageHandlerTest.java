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
import org.drasyl.peer.connection.message.QuitMessage;
import org.drasyl.peer.connection.message.StatusMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.drasyl.peer.connection.message.StatusMessage.Code.STATUS_OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class QuitMessageHandlerTest {
    private QuitMessageHandler handler;
    private EmbeddedChannel channel;

    @BeforeEach
    void setUp() {
        handler = QuitMessageHandler.INSTANCE;
        channel = new EmbeddedChannel(handler);
    }

    @Test
    void shouldReplyWithStatusOkAndThenCloseChannel() {
        QuitMessage quitMessage = new QuitMessage();
        channel.writeInbound(quitMessage);
        channel.flush();

        assertEquals(new StatusMessage(STATUS_OK, quitMessage.getId()), channel.readOutbound());
        assertFalse(channel.isOpen());
    }
}
