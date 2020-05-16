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

import io.netty.channel.ChannelHandlerContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class WrongProtocolFilterTest {
    private ChannelHandlerContext ctx;

    @BeforeEach
    void setUp() {
        ctx = mock(ChannelHandlerContext.class);
    }

    @Test
    void channelRead0ShouldThrowExceptionIfClientApparentlyUsingWrongProtocol() {
        WrongProtocolFilter filter = WrongProtocolFilter.INSTANCE;

        WrongProtocolFilter.FILTER.forEach(input -> assertThrows(IllegalArgumentException.class, () -> {
            filter.channelRead0(ctx, input);
            verify(ctx, never()).fireChannelRead(input);
        }));
    }

    @Test
    void channelRead0ShouldPassThroughMessageIfClientApparentlyUsingCorrectProtocol() throws Exception {
        WrongProtocolFilter filter = WrongProtocolFilter.INSTANCE;

        filter.channelRead0(ctx, "Test");
        verify(ctx).fireChannelRead("Test");
    }
}