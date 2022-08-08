/*
 * Copyright (c) 2020-2022 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.util;

import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static io.netty.util.concurrent.ImmediateEventExecutor.INSTANCE;
import static org.drasyl.util.FutureComposer.composeFailedFuture;
import static org.drasyl.util.FutureComposer.composeFuture;
import static org.drasyl.util.FutureComposer.composeSucceededFuture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class FutureComposerTest {
    @Nested
    class ThenWithFuture {
        @Test
        void composedFutureShouldCompleteOnlyWhenChainIsCompleted() {
            final Promise<Void> promise1 = new DefaultPromise<>(INSTANCE);
            final Promise<Void> promise2 = new DefaultPromise<>(INSTANCE);

            final Future<Void> composedFuture = composeFuture(promise1).then(promise2).finish(INSTANCE);

            assertFalse(composedFuture.isDone());
            promise2.setSuccess(null);
            assertFalse(composedFuture.isDone());
            promise1.setSuccess(null);
            assertTrue(composedFuture.isDone());
        }

        @Test
        void composedFutureShouldCompleteOnlyWhenChainIsCompleted2() {
            final Promise<Void> promise1 = new DefaultPromise<>(INSTANCE);
            final Promise<Void> promise2 = new DefaultPromise<>(INSTANCE);

            final Future<Void> composedFuture = composeFuture(promise1).then(promise2).finish(INSTANCE);

            assertFalse(composedFuture.isDone());
            promise1.setSuccess(null);
            assertFalse(composedFuture.isDone());
            promise2.setSuccess(null);
            assertTrue(composedFuture.isDone());
        }
    }

    @Nested
    class ThenWithMappedComposer {
        @Test
        void composedFutureShouldCompleteOnlyWhenChainIsCompleted() {
            final Promise<Void> promise1 = new DefaultPromise<>(INSTANCE);
            final Promise<Void> promise2 = new DefaultPromise<>(INSTANCE);

            final Future<Void> composedFuture = composeFuture(promise1).then(f -> composeSucceededFuture().then(promise2)).finish(INSTANCE);

            assertFalse(composedFuture.isDone());
            promise2.setSuccess(null);
            assertFalse(composedFuture.isDone());
            promise1.setSuccess(null);
            assertTrue(composedFuture.isDone());
        }

        @Test
        void composedFutureShouldCompleteOnlyWhenChainIsCompleted2() {
            final Promise<Void> promise1 = new DefaultPromise<>(INSTANCE);
            final Promise<Void> promise2 = new DefaultPromise<>(INSTANCE);

            final Future<Void> composedFuture = composeFuture(promise1).then(f -> composeSucceededFuture().then(promise2)).finish(INSTANCE);

            assertFalse(composedFuture.isDone());
            promise1.setSuccess(null);
            assertFalse(composedFuture.isDone());
            promise2.setSuccess(null);
            assertTrue(composedFuture.isDone());
        }
    }

    @Nested
    class ThenWithSuppliedComposer {
        @Test
        void composedFutureShouldCompleteOnlyWhenChainIsCompleted() {
            final Promise<Void> promise1 = new DefaultPromise<>(INSTANCE);
            final Promise<Void> promise2 = new DefaultPromise<>(INSTANCE);

            final Future<Void> composedFuture = composeFuture(promise1).then(() -> composeSucceededFuture().then(promise2)).finish(INSTANCE);

            assertFalse(composedFuture.isDone());
            promise2.setSuccess(null);
            assertFalse(composedFuture.isDone());
            promise1.setSuccess(null);
            assertTrue(composedFuture.isDone());
        }

        @Test
        void composedFutureShouldCompleteOnlyWhenChainIsCompleted2() {
            final Promise<Void> promise1 = new DefaultPromise<>(INSTANCE);
            final Promise<Void> promise2 = new DefaultPromise<>(INSTANCE);

            final Future<Void> composedFuture = composeFuture(promise1).then(() -> composeSucceededFuture().then(promise2)).finish(INSTANCE);

            assertFalse(composedFuture.isDone());
            promise1.setSuccess(null);
            assertFalse(composedFuture.isDone());
            promise2.setSuccess(null);
            assertTrue(composedFuture.isDone());
        }
    }

    @Nested
    class ComposeFailedFuture {
        @Test
        void shouldReturnedFailedFuture(@Mock Throwable cause) {
            final Future<Object> composedFuture = composeFailedFuture(cause).finish(INSTANCE);

            assertTrue(composedFuture.isDone());
            assertFalse(composedFuture.isSuccess());
            assertEquals(cause, composedFuture.cause());
        }
    }
}
