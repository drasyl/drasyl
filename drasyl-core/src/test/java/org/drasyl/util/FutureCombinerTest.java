/*
 * Copyright (c) 2020-2021.
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
                    .combine(futureToComplete)
                    .complete(null);

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
