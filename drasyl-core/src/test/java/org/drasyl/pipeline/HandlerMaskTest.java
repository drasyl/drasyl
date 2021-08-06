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
package org.drasyl.pipeline;

import io.netty.channel.ChannelHandlerContext;
import org.drasyl.event.Event;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.skeleton.HandlerAdapter;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HandlerMaskTest {
    @Nested
    class Mask {
        @Test
        void shouldCalcCorrectMaskForAllSkip() {
            assertEquals(0, HandlerMask.mask(HandlerAdapter.class));
        }

        @Test
        void shouldCalcCorrectMaskForNoSkip() {
            assertEquals(HandlerMask.ALL, HandlerMask.mask(HandlerAdapter.class));
        }
    }

    @Nested
    class Skippable {
        @Test
        void shouldReturnFalseForAdded() {
            assertFalse(HandlerMask.isSkippable(HandlerAdapter.class, "onAdded", ChannelHandlerContext.class));
        }

        @Test
        void shouldReturnFalseForRemoved() {
            assertFalse(HandlerMask.isSkippable(HandlerAdapter.class, "onRemoved", ChannelHandlerContext.class));
        }

        @Test
        void shouldReturnTrueForWrite() {
            assertTrue(HandlerMask.isSkippable(HandlerAdapter.class, "onOutbound", ChannelHandlerContext.class, Address.class, Object.class, CompletableFuture.class));
        }

        @Test
        void shouldReturnTrueForRead() {
            assertTrue(HandlerMask.isSkippable(HandlerAdapter.class, "onInbound", ChannelHandlerContext.class, Address.class, Object.class, CompletableFuture.class));
        }

        @Test
        void shouldReturnTrueForEventTriggered() {
            assertTrue(HandlerMask.isSkippable(HandlerAdapter.class, "onEvent", ChannelHandlerContext.class, Event.class, CompletableFuture.class));
        }

        @Test
        void shouldReturnTrueForExceptionCaught() {
            assertTrue(HandlerMask.isSkippable(HandlerAdapter.class, "onException", ChannelHandlerContext.class, Exception.class));
        }

        @Test
        void shouldReturnFalseOnError() {
            assertFalse(HandlerMask.isSkippable(HandlerAdapter.class, "foo", Object.class));
        }
    }
}
