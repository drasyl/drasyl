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

import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.Promise;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

import static org.drasyl.util.FutureUtil.synchronizeFutures;
import static org.drasyl.util.FutureUtil.toFuture;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
        void shouldTranslateFailedPromise(@Mock final EventExecutor executor,
                                          @Mock final Throwable throwable) {
            final DefaultPromise<Object> promise = new DefaultPromise<>(executor);
            promise.setFailure(throwable);

            final CompletableFuture<Object> future = toFuture(promise);

            assertTrue(future.isCompletedExceptionally());
            assertThrows(CompletionException.class, () -> future.getNow(null));
        }

        @SuppressWarnings("unchecked")
        @Test
        void shouldTranslateSucceedingPromise(@Mock final Promise<String> promise) {
            when(promise.addListener(any())).then(invocation -> {
                final GenericFutureListener<Promise<String>> listener = invocation.getArgument(0, GenericFutureListener.class);
                when(promise.isSuccess()).thenReturn(true);
                when(promise.getNow()).thenReturn("Hallo");
                listener.operationComplete(promise);
                return null;
            });

            final CompletableFuture<String> future = toFuture(promise);

            assertTrue(future.isDone());
            assertEquals("Hallo", future.getNow(null));
        }

        @SuppressWarnings("unchecked")
        @Test
        void shouldTranslateFailingPromise(@Mock final Promise<Object> promise,
                                           @Mock final Throwable throwable) {
            when(promise.addListener(any())).then(invocation -> {
                final GenericFutureListener<Promise<Object>> listener = invocation.getArgument(0, GenericFutureListener.class);
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
    class SynchronizeFutures {
        @Test
        void shouldSucceedPromiseIfFutureHasBeenSucceeeded(@Mock final EventExecutor executor) {
            final Promise<String> promise = new DefaultPromise<>(executor);
            final CompletableFuture<String> future = new CompletableFuture<>();
            synchronizeFutures(promise, future);

            future.complete("Hello");

            assertEquals("Hello", promise.getNow());
        }

        @Test
        void shouldFailPromiseIfFutureHasBeenFailed(@Mock final EventExecutor executor,
                                                    @Mock final Throwable ex) {
            final Promise<String> promise = new DefaultPromise<>(executor);
            final CompletableFuture<String> future = new CompletableFuture<>();
            synchronizeFutures(promise, future);

            future.completeExceptionally(ex);

            assertEquals(ex, promise.cause());
        }

        @Test
        void shouldSucceedFutureIfPromiseHasBeenSucceeeded(@Mock final EventExecutor executor) {
            when(executor.inEventLoop()).thenReturn(true);

            final Promise<String> promise = new DefaultPromise<>(executor);
            final CompletableFuture<String> future = new CompletableFuture<>();
            synchronizeFutures(promise, future);

            promise.setSuccess("Hello");

            assertEquals("Hello", future.getNow(null));
        }

        @Test
        void shouldFailFutureIfPromiseHasBeenFailed(@Mock final EventExecutor executor,
                                                    @Mock final Throwable cause) {
            when(executor.inEventLoop()).thenReturn(true);

            final Promise<String> promise = new DefaultPromise<>(executor);
            final CompletableFuture<String> future = new CompletableFuture<>();
            synchronizeFutures(promise, future);

            promise.setFailure(cause);

            assertThrows(ExecutionException.class, future::get);
        }
    }

    @Nested
    class MapFuture {
        @Test
        void shouldApplyMapperFunctionWhenFutureSucceeds() {
            final Promise<Integer> promise = new DefaultPromise<>(ImmediateEventExecutor.INSTANCE);
            final Future<String> mappedFuture = FutureUtil.mapFuture(promise, ImmediateEventExecutor.INSTANCE, Object::toString);
            promise.setSuccess(42);

            assertEquals("42", mappedFuture.getNow());
        }

        @Test
        void shouldApplyMapperFunctionOnSucceededFuture() {
            final Promise<Integer> promise = new DefaultPromise<>(ImmediateEventExecutor.INSTANCE);
            promise.setSuccess(42);
            final Future<String> mappedFuture = FutureUtil.mapFuture(promise, ImmediateEventExecutor.INSTANCE, Object::toString);

            assertEquals("42", mappedFuture.getNow());
        }

        @Test
        void shouldPassFailureWhenFutureFails(@Mock final Throwable cause) {
            final Promise<Integer> promise = new DefaultPromise<>(ImmediateEventExecutor.INSTANCE);
            final Future<String> mappedFuture = FutureUtil.mapFuture(promise, ImmediateEventExecutor.INSTANCE, Object::toString);
            promise.setFailure(cause);

            assertEquals(cause, mappedFuture.cause());
        }

        @Test
        void shouldPassFailureOnFailedFuture(@Mock final Throwable cause) {
            final Promise<Integer> promise = new DefaultPromise<>(ImmediateEventExecutor.INSTANCE);
            promise.setFailure(cause);
            final Future<String> mappedFuture = FutureUtil.mapFuture(promise, ImmediateEventExecutor.INSTANCE, Object::toString);

            assertEquals(cause, mappedFuture.cause());
        }

        @SuppressWarnings({ "rawtypes", "unchecked" })
        @Test
        void shouldCreateFailedFutureIfMapperFailsWhenFutureFails(@Mock final Function mapper) {
            when(mapper.apply(any())).thenThrow(RuntimeException.class);

            final Promise<Integer> promise = new DefaultPromise<>(ImmediateEventExecutor.INSTANCE);
            final Future<String> mappedFuture = FutureUtil.mapFuture(promise, ImmediateEventExecutor.INSTANCE, mapper);
            promise.setSuccess(42);

            assertThat(mappedFuture.cause(), instanceOf(RuntimeException.class));
        }

        @SuppressWarnings({ "rawtypes", "unchecked" })
        @Test
        void shouldCreateFailedFutureIfMapperFailsOnSucceededFuture(@Mock final Function mapper) {
            when(mapper.apply(any())).thenThrow(RuntimeException.class);

            final Promise<Integer> promise = new DefaultPromise<>(ImmediateEventExecutor.INSTANCE);
            promise.setSuccess(42);
            final Future<String> mappedFuture = FutureUtil.mapFuture(promise, ImmediateEventExecutor.INSTANCE, mapper);

            assertThat(mappedFuture.cause(), instanceOf(RuntimeException.class));
        }

        @Test
        void shouldCancelFutureIfMappedFutureIsCanceled() {
            final Promise<Integer> promise = new DefaultPromise<>(ImmediateEventExecutor.INSTANCE);
            promise.cancel(false);
            final Future<String> mappedFuture = FutureUtil.mapFuture(promise, ImmediateEventExecutor.INSTANCE, Object::toString);
            mappedFuture.cancel(false);

            assertTrue(promise.isCancelled());
        }
    }

    @Nested
    class ChainFuture {

    }
}
