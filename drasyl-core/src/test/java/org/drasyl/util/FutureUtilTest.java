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
        void shouldTranslateSucceededPromise(@Mock final EventExecutor executor) {
            final DefaultPromise<Object> promise = new DefaultPromise<>(executor);
            promise.setSuccess("Hallo");

            final CompletableFuture<Object> future = toFuture(promise);

            assertTrue(future.isDone());
            assertEquals("Hallo", future.getNow(null));
        }

        @Test
        void shouldTranslateFailedPromise(@Mock final EventExecutor executor, @Mock final Throwable throwable) {
            final DefaultPromise<Object> promise = new DefaultPromise<>(executor);
            promise.setFailure(throwable);

            final CompletableFuture<Object> future = toFuture(promise);

            assertTrue(future.isCompletedExceptionally());
            assertThrows(CompletionException.class, () -> future.getNow(null));
        }

        @Test
        void shouldTranslateSucceedingPromise(@Mock final Promise promise) {
            when(promise.addListener(any())).then(invocation -> {
                final GenericFutureListener listener = invocation.getArgument(0, GenericFutureListener.class);
                when(promise.isSuccess()).thenReturn(true);
                when(promise.getNow()).thenReturn("Hallo");
                listener.operationComplete(promise);
                return null;
            });

            final CompletableFuture<Object> future = toFuture(promise);

            assertTrue(future.isDone());
            assertEquals("Hallo", future.getNow(null));
        }

        @Test
        void shouldTranslateFailingPromise(@Mock final Promise promise, @Mock final Throwable throwable) {
            when(promise.addListener(any())).then(invocation -> {
                final GenericFutureListener listener = invocation.getArgument(0, GenericFutureListener.class);
                when(promise.cause()).thenReturn(throwable);
                listener.operationComplete(promise);
                return null;
            });

            final CompletableFuture<Object> future = toFuture(promise);
            promise.setFailure(throwable);

            assertTrue(future.isCompletedExceptionally());
            assertThrows(CompletionException.class, () -> future.getNow(null));
        }
    }

    @Nested
    class CompleteOnAnyOfExceptionally {
        @Test
        void shouldCompleteFutureIfAnyFutureInCollectionCompletesExceptionally() {
            final CompletableFuture<?> future1 = new CompletableFuture<>();
            final CompletableFuture<?> future2 = new CompletableFuture<>();
            final CompletableFuture<Void> futureToComplete = new CompletableFuture<>();
            final Collection<CompletableFuture<?>> futures = List.of(future1, future2);

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
            final CompletableFuture<Void> futureToComplete = new CompletableFuture<>();

            FutureUtil.completeOnAnyOfExceptionally(futureToComplete);

            futureToComplete.complete(null);

            assertTrue(futureToComplete.isDone());
            assertFalse(futureToComplete.isCompletedExceptionally());
        }

        @Test
        void shouldThrowExceptionIfParamIsNull(@Mock final CompletableFuture<Void> future) {
            assertThrows(NullPointerException.class, () -> FutureUtil.completeOnAnyOfExceptionally(future, (CompletableFuture<?>) null));
            assertThrows(NullPointerException.class, () -> FutureUtil.completeOnAnyOfExceptionally(null));
            assertThrows(NullPointerException.class, () -> FutureUtil.completeOnAnyOfExceptionally(null, (Collection<CompletableFuture<?>>) null));
        }
    }

    @Nested
    class CompleteOnAllOf {
        @Test
        void shouldCompleteFutureIfAnyFutureInCollectionCompletesExceptionally() {
            final CompletableFuture<?> future1 = new CompletableFuture<>();
            final CompletableFuture<?> future2 = new CompletableFuture<>();
            final Collection<CompletableFuture<?>> futures = List.of(future1, future2);

            final CompletableFuture<Void> futureToComplete = FutureUtil.getCompleteOnAllOf(futures);

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
            final CompletableFuture<Void> future1 = new CompletableFuture<>();
            final CompletableFuture<Object> future2 = new CompletableFuture<>();
            final Collection<CompletableFuture<?>> futures = List.of(future1, future2);

            final CompletableFuture<Void> futureToComplete = FutureUtil.getCompleteOnAllOf(futures);

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
            final CompletableFuture<Void> future1 = new CompletableFuture<>();
            final CompletableFuture<Object> future2 = new CompletableFuture<>();
            final Collection<CompletableFuture<?>> futures = List.of(future1, future2);

            final CompletableFuture<Void> futureToComplete = FutureUtil.getCompleteOnAllOf(futures);

            future1.complete(null);

            assertTrue(future1.isDone());
            assertFalse(future2.isDone());
            assertFalse(futureToComplete.isDone());
            assertFalse(future1.isCompletedExceptionally());
        }

        @Test
        void shouldReturnImmediatelyWhenListIsEmpty() {
            final CompletableFuture<Void> futureToComplete = FutureUtil.getCompleteOnAllOf();

            futureToComplete.complete(null);

            assertTrue(futureToComplete.isDone());
            assertFalse(futureToComplete.isCompletedExceptionally());
        }

        @Test
        void shouldThrowExceptionIfParamIsNull(@Mock final CompletableFuture<Void> future) {
            assertThrows(NullPointerException.class, () -> FutureUtil.completeOnAllOf(future, (CompletableFuture<?>) null));
            assertThrows(NullPointerException.class, () -> FutureUtil.completeOnAllOf(null));
            assertThrows(NullPointerException.class, () -> FutureUtil.completeOnAllOf(null, (Collection<CompletableFuture<?>>) null));
            assertThrows(NullPointerException.class, () -> FutureUtil.getCompleteOnAllOf((CompletableFuture<?>) null));
            assertThrows(NullPointerException.class, () -> FutureUtil.getCompleteOnAllOf((Collection<CompletableFuture<?>>) null));
        }
    }
}