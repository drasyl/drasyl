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
package org.drasyl.pipeline;

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
            assertEquals(HandlerMask.ALL, HandlerMask.mask(Handler.class));
        }
    }

    @Nested
    class Skippable {
        @Test
        void shouldReturnFalseForAdded() {
            assertFalse(HandlerMask.isSkippable(HandlerAdapter.class, "handlerAdded", HandlerContext.class));
        }

        @Test
        void shouldReturnFalseForRemoved() {
            assertFalse(HandlerMask.isSkippable(HandlerAdapter.class, "handlerRemoved", HandlerContext.class));
        }

        @Test
        void shouldReturnTrueForWrite() {
            assertTrue(HandlerMask.isSkippable(HandlerAdapter.class, "write", HandlerContext.class, Address.class, Object.class, CompletableFuture.class));
        }

        @Test
        void shouldReturnTrueForRead() {
            assertTrue(HandlerMask.isSkippable(HandlerAdapter.class, "read", HandlerContext.class, Address.class, Object.class, CompletableFuture.class));
        }

        @Test
        void shouldReturnTrueForEventTriggered() {
            assertTrue(HandlerMask.isSkippable(HandlerAdapter.class, "eventTriggered", HandlerContext.class, Event.class, CompletableFuture.class));
        }

        @Test
        void shouldReturnTrueForExceptionCaught() {
            assertTrue(HandlerMask.isSkippable(HandlerAdapter.class, "exceptionCaught", HandlerContext.class, Exception.class));
        }

        @Test
        void shouldReturnFalseOnError() {
            assertFalse(HandlerMask.isSkippable(HandlerAdapter.class, "foo", Object.class));
        }
    }
}