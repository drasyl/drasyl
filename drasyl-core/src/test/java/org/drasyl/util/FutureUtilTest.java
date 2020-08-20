package org.drasyl.util;

import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.drasyl.util.FutureUtil.toFuture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FutureUtilTest {
    @Nested
    class ToFuture {
        @Test
        void shouldTranslateSucceededPromise(@Mock EventExecutor executor) {
            DefaultPromise<Object> promise = new DefaultPromise<>(executor);
            promise.setSuccess("Hallo");

            CompletableFuture<Object> future = toFuture(promise);

            assertTrue(future.isDone());
            assertEquals("Hallo", future.getNow(null));
        }

        @Test
        void shouldTranslateFailedPromise(@Mock EventExecutor executor, @Mock Throwable throwable) {
            DefaultPromise<Object> promise = new DefaultPromise<>(executor);
            promise.setFailure(throwable);

            CompletableFuture<Object> future = toFuture(promise);

            assertTrue(future.isCompletedExceptionally());
            assertThrows(CompletionException.class, () -> future.getNow(null));
        }

        @Test
        void shouldTranslateSucceedingPromise(@Mock Promise promise) {
            when(promise.addListener(any())).then(invocation -> {
                GenericFutureListener listener = invocation.getArgument(0, GenericFutureListener.class);
                when(promise.isSuccess()).thenReturn(true);
                when(promise.getNow()).thenReturn("Hallo");
                listener.operationComplete(promise);
                return null;
            });

            CompletableFuture<Object> future = toFuture(promise);

            assertTrue(future.isDone());
            assertEquals("Hallo", future.getNow(null));
        }

        @Test
        void shouldTranslateFailingPromise(@Mock Promise promise, @Mock Throwable throwable) {
            when(promise.addListener(any())).then(invocation -> {
                GenericFutureListener listener = invocation.getArgument(0, GenericFutureListener.class);
                when(promise.cause()).thenReturn(throwable);
                listener.operationComplete(promise);
                return null;
            });

            CompletableFuture<Object> future = toFuture(promise);
            promise.setFailure(throwable);

            assertTrue(future.isCompletedExceptionally());
            assertThrows(CompletionException.class, () -> future.getNow(null));
        }
    }

    @Nested
    class CompleteOnAnyOfExceptionally {
        @Test
        void shouldCompleteFutureIfAnyFutureInCollectionCompletesExceptionally() {
            CompletableFuture<?> future1 = new CompletableFuture<>();
            CompletableFuture<?> future2 = new CompletableFuture<>();
            CompletableFuture<Void> futureToComplete = new CompletableFuture<>();
            Collection<CompletableFuture<?>> futures = List.of(future1, future2);

            FutureUtil.completeOnAnyOfExceptionally(futureToComplete, futures);

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
        void shouldReturnImmediatelyWhenListIsEmpty() {
            CompletableFuture<Void> futureToComplete = new CompletableFuture<>();

            FutureUtil.completeOnAnyOfExceptionally(futureToComplete);

            futureToComplete.complete(null);

            assertTrue(futureToComplete.isDone());
            assertFalse(futureToComplete.isCompletedExceptionally());
        }

        @Test
        void shouldThrowExceptionIfParamIsNull(@Mock CompletableFuture<Void> future) {
            assertThrows(NullPointerException.class, () -> FutureUtil.completeOnAnyOfExceptionally(future, (CompletableFuture<?>) null));
            assertThrows(NullPointerException.class, () -> FutureUtil.completeOnAnyOfExceptionally(null));
            assertThrows(NullPointerException.class, () -> FutureUtil.completeOnAnyOfExceptionally(null, (Collection<CompletableFuture<?>>) null));
        }
    }

    @Nested
    class CompleteOnAllOf {
        @Test
        void shouldCompleteFutureIfAnyFutureInCollectionCompletesExceptionally() {
            CompletableFuture<?> future1 = new CompletableFuture<>();
            CompletableFuture<?> future2 = new CompletableFuture<>();
            Collection<CompletableFuture<?>> futures = List.of(future1, future2);

            CompletableFuture<Void> futureToComplete = FutureUtil.getCompleteOnAllOf(futures);

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
        void shouldCompleteFutureIfAllFuturesInCollectionCompletesNormally() {
            CompletableFuture<Void> future1 = new CompletableFuture<>();
            CompletableFuture<Object> future2 = new CompletableFuture<>();
            Collection<CompletableFuture<?>> futures = List.of(future1, future2);

            CompletableFuture<Void> futureToComplete = FutureUtil.getCompleteOnAllOf(futures);

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
            CompletableFuture<Void> future1 = new CompletableFuture<>();
            CompletableFuture<Object> future2 = new CompletableFuture<>();
            Collection<CompletableFuture<?>> futures = List.of(future1, future2);

            CompletableFuture<Void> futureToComplete = FutureUtil.getCompleteOnAllOf(futures);

            future1.complete(null);

            assertTrue(future1.isDone());
            assertFalse(future2.isDone());
            assertFalse(futureToComplete.isDone());
            assertFalse(future1.isCompletedExceptionally());
        }

        @Test
        void shouldReturnImmediatelyWhenListIsEmpty() {
            CompletableFuture<Void> futureToComplete = FutureUtil.getCompleteOnAllOf();

            futureToComplete.complete(null);

            assertTrue(futureToComplete.isDone());
            assertFalse(futureToComplete.isCompletedExceptionally());
        }

        @Test
        void shouldThrowExceptionIfParamIsNull(@Mock CompletableFuture<Void> future) {
            assertThrows(NullPointerException.class, () -> FutureUtil.completeOnAllOf(future, (CompletableFuture<?>) null));
            assertThrows(NullPointerException.class, () -> FutureUtil.completeOnAllOf(null));
            assertThrows(NullPointerException.class, () -> FutureUtil.completeOnAllOf(null, (Collection<CompletableFuture<?>>) null));
            assertThrows(NullPointerException.class, () -> FutureUtil.getCompleteOnAllOf((CompletableFuture<?>) null));
            assertThrows(NullPointerException.class, () -> FutureUtil.getCompleteOnAllOf((Collection<CompletableFuture<?>>) null));
        }
    }
}