/*
 * Copyright (c) 2020
 *
 * This file is part of Relayserver.
 *
 * Relayserver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Relayserver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Relayserver.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.drasyl.core.common.handler;

import io.netty.channel.ChannelHandlerContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WrongProtocolFilterTest {
    private ChannelHandlerContext ctx;

    @BeforeEach
    void setUp() {
        ctx = mock(ChannelHandlerContext.class);
    }

    // should throw IllegalArgumentException on every matching string
    @Test
    void channelRead0ThrowExceptionOnMatch() {
        WrongProtocolFilter filter = WrongProtocolFilter.INSTANCE;

        WrongProtocolFilter.FILTER.forEach(input -> {
            assertThrows(IllegalArgumentException.class, () -> {
                filter.channelRead0(ctx, input);
                verify(ctx, never()).fireChannelRead(input);
            });
        });
    }

    // should pass the string to the next handler in the pipeline
    @Test
    void channelRead0PassWhenNoMatch() throws Exception {
        WrongProtocolFilter filter = WrongProtocolFilter.INSTANCE;

        filter.channelRead0(ctx, "Test");
        verify(ctx, times(1)).fireChannelRead("Test");
    }
}