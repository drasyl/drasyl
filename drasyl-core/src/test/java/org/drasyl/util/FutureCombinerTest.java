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
package org.drasyl.util;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class FutureCombinerTest {
    @Nested
    class CompleteOnAnyOfExceptionally {
        @Test
        void shouldCompleteFutureIfAnyFutureInCollectionCompletesExceptionally() {
            final CompletableFuture<?> future1 = new CompletableFuture<>();
            final CompletableFuture<?> future2 = new CompletableFuture<>();
            final CompletableFuture<Void> futureToComplete = new CompletableFuture<>();

            FutureCombiner.getInstance()
                    .addAll(future1, future2)
                    .combine(futureToComplete);

            future1.completeExceptionally(new Exception());

            assertTrue(futureToComplete.isDone());
            assertTrue(futureToComplete.isCompletedExceptionally());
            assertTrue(future1.isDone());
            assertTrue(future1.isCompletedExceptionally());
            assertFalse(future2.isDone());
            assertThrows(Exception.class, future1::join);
            assertThrows(Exception.class, futureToComplete::join);
        }

        @Test
        void shouldCompleteFutureExceptionallyIfAnyFutureInCollectionIsAlreadyCompletedExceptionally() {
            final CompletableFuture<?> future1 = failedFuture(new Exception());
            final CompletableFuture<?> future2 = new CompletableFuture<>();
            final CompletableFuture<Void> futureToComplete = new CompletableFuture<>();

            FutureCombiner.getInstance()
                    .addAll(future1, future2)
                    .combine(futureToComplete);

            assertTrue(futureToComplete.isDone());
            assertTrue(futureToComplete.isCompletedExceptionally());
            assertTrue(future1.isDone());
            assertTrue(future1.isCompletedExceptionally());
            assertFalse(future2.isDone());
            assertThrows(Exception.class, future1::join);
            assertThrows(Exception.class, futureToComplete::join);
        }

        @Test
        void shouldCompleteFutureExceptionallyIfFutureItSelfCompletesExceptionally() {
            final CompletableFuture<Void> future = new CompletableFuture<>();
            final CompletableFuture<?> future1 = new CompletableFuture<>();
            final CompletableFuture<?> future2 = new CompletableFuture<>();

            FutureCombiner.getInstance()
                    .addAll(future1, future2)
                    .combine(future)
                    .completeExceptionally(new Exception());

            assertTrue(future.isDone());
            assertTrue(future.isCompletedExceptionally());
            assertFalse(future1.isDone());
            assertFalse(future2.isDone());
            assertThrows(Exception.class, future::join);
        }

        @Test
        void shouldReturnImmediatelyWhenListIsEmpty() {
            final CompletableFuture<Void> futureToComplete = new CompletableFuture<>();

            FutureCombiner.getInstance()
                    .combine(futureToComplete)
                    .complete(null);

            assertTrue(futureToComplete.isDone());
            assertFalse(futureToComplete.isCompletedExceptionally());
        }

        @SuppressWarnings("ConfusingArgumentToVarargsMethod")
        @Test
        void shouldThrowExceptionIfParamIsNull() {
            final FutureCombiner combiner = FutureCombiner.getInstance();

            assertThrows(NullPointerException.class, () -> combiner.add(null));
            assertThrows(NullPointerException.class, () -> combiner.addAll(null));
            assertThrows(NullPointerException.class, () -> combiner.combine(null));
        }
    }

    @Nested
    class CompleteOnAllOf {
        @Test
        void shouldCompleteFutureIfAnyFutureInCollectionCompletesExceptionally() {
            final CompletableFuture<?> future1 = new CompletableFuture<>();
            final CompletableFuture<?> future2 = new CompletableFuture<>();
            final CompletableFuture<Void> futureToComplete = new CompletableFuture<>();

            FutureCombiner.getInstance()
                    .addAll(future1, future2)
                    .combine(futureToComplete);

            future1.completeExceptionally(new Exception());

            assertTrue(futureToComplete.isDone());
            assertTrue(futureToComplete.isCompletedExceptionally());
            assertTrue(future1.isDone());
            assertTrue(future1.isCompletedExceptionally());
            assertFalse(future2.isDone());
            assertThrows(Exception.class, future1::join);
            assertThrows(Exception.class, futureToComplete::join);
        }

        @Test
        void shouldCompleteFutureIfAnyFutureInCollectionIsAlreadyCompletedExceptionally() {
            final CompletableFuture<?> future1 = failedFuture(new Exception());
            final CompletableFuture<?> future2 = new CompletableFuture<>();
            final CompletableFuture<Void> futureToComplete = new CompletableFuture<>();

            FutureCombiner.getInstance()
                    .addAll(future1, future2)
                    .combine(futureToComplete);

            assertTrue(futureToComplete.isDone());
            assertTrue(futureToComplete.isCompletedExceptionally());
            assertTrue(future1.isDone());
            assertTrue(future1.isCompletedExceptionally());
            assertFalse(future2.isDone());
            assertThrows(Exception.class, future1::join);
            assertThrows(Exception.class, futureToComplete::join);
        }

        @Test
        void shouldCompleteFutureIfFutureItSelfCompletesExceptionally() {
            final CompletableFuture<Void> future = new CompletableFuture<>();
            final CompletableFuture<?> future1 = new CompletableFuture<>();
            final CompletableFuture<?> future2 = new CompletableFuture<>();

            FutureCombiner.getInstance()
                    .addAll(future1, future2)
                    .combine(future)
                    .completeExceptionally(new Exception());

            assertTrue(future.isDone());
            assertTrue(future.isCompletedExceptionally());
            assertFalse(future1.isDone());
            assertFalse(future2.isDone());
            assertThrows(Exception.class, future::join);
        }

        @Test
        void shouldCompleteFutureIfAllFuturesInCollectionCompletesNormally() {
            final CompletableFuture<Void> future1 = new CompletableFuture<>();
            final CompletableFuture<Object> future2 = new CompletableFuture<>();
            final CompletableFuture<Void> futureToComplete = new CompletableFuture<>();

            FutureCombiner.getInstance()
                    .addAll(future1, future2)
                    .combine(futureToComplete);

            future1.complete(null);
            future2.complete(new Object());

            assertTrue(future1.isDone());
            assertTrue(future2.isDone());
            assertTrue(futureToComplete.isDone());
            assertFalse(future1.isCompletedExceptionally());
            assertFalse(future2.isCompletedExceptionally());
            assertFalse(futureToComplete.isCompletedExceptionally());
        }

        @Test
        void shouldNotCompleteFutureIfAnyFuturesInCollectionIsNotCompleted() {
            final CompletableFuture<Void> future1 = completedFuture(null);
            final CompletableFuture<Object> future2 = new CompletableFuture<>();
            final CompletableFuture<Void> futureToComplete = new CompletableFuture<>();

            FutureCombiner.getInstance()
                    .addAll(future1, future2)
                    .combine(futureToComplete);

            assertTrue(future1.isDone());
            assertFalse(future2.isDone());
            assertFalse(futureToComplete.isDone());
            assertFalse(future1.isCompletedExceptionally());
        }

        @Test
        void shouldReturnImmediatelyWhenListIsEmpty() {
            final CompletableFuture<Void> futureToComplete = new CompletableFuture<>();

            FutureCombiner.getInstance()
                    .addAll()
                    .combine(futureToComplete);

            assertTrue(futureToComplete.isDone());
            assertFalse(futureToComplete.isCompletedExceptionally());
        }

        @Test
        void shouldCompleteExceptionallyIfOneGivenFutureHasAlreadyCompletedExceptionally() {
            final CompletableFuture<Void> future = new CompletableFuture<>();
            final CompletableFuture<?> future1 = completedFuture(null);
            final CompletableFuture<?> future2 = failedFuture(new Exception());

            FutureCombiner.getInstance()
                    .addAll(future1, future2)
                    .combine(future);

            assertTrue(future1.isDone());
            assertTrue(future2.isDone());
            assertTrue(future.isDone());
            assertTrue(future2.isCompletedExceptionally());
            assertTrue(future.isCompletedExceptionally());
            assertThrows(Exception.class, future::join);
        }

        @Test
        void shouldCompleteIfAllGivenFutureHasAlreadyCompleted() {
            final CompletableFuture<Void> future = new CompletableFuture<>();
            final CompletableFuture<?> future1 = completedFuture(null);
            final CompletableFuture<?> future2 = completedFuture(null);

            FutureCombiner.getInstance()
                    .addAll(future1, future2)
                    .combine(future);

            assertTrue(future1.isDone());
            assertTrue(future2.isDone());
            assertTrue(future.isDone());
        }
    }
}
