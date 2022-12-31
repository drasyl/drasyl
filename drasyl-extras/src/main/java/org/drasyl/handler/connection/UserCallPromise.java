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
package org.drasyl.handler.connection;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static java.util.Objects.requireNonNull;

public class UserCallPromise implements ChannelPromise {
    private final UserCall userCall;
    private final ChannelPromise promise;
    UserCallPromise(final UserCall userCall, final ChannelPromise promise) {
        this.userCall = requireNonNull(userCall);
        this.promise = requireNonNull(promise);
    }

    public static UserCallPromise newPromise(final ChannelHandlerContext ctx,
                                             final UserCall userCall) {
        return new UserCallPromise(userCall, ctx.newPromise());
    }

    public static UserCallPromise wrapPromise(final UserCall userCall,
                                              final ChannelPromise promise) {
        return new UserCallPromise(userCall, promise);
    }

    public UserCall userCall() {
        return userCall;
    }

    @Override
    public Channel channel() {
        return promise.channel();
    }

    @Override
    public ChannelPromise setSuccess(final Void result) {
        return promise.setSuccess(result);
    }

    @Override
    public boolean trySuccess(final Void result) {
        return promise.trySuccess(result);
    }

    @Override
    public ChannelPromise setSuccess() {
        return promise.setSuccess();
    }

    @Override
    public boolean trySuccess() {
        return promise.trySuccess();
    }

    @Override
    public ChannelPromise setFailure(final Throwable cause) {
        return promise.setFailure(cause);
    }

    @Override
    public boolean tryFailure(final Throwable cause) {
        return promise.tryFailure(cause);
    }

    @Override
    public boolean setUncancellable() {
        return promise.setUncancellable();
    }

    @Override
    public boolean isSuccess() {
        return promise.isSuccess();
    }

    @Override
    public boolean isCancellable() {
        return promise.isCancellable();
    }

    @Override
    public Throwable cause() {
        return promise.cause();
    }

    @Override
    public ChannelPromise addListener(final GenericFutureListener<? extends Future<? super Void>> listener) {
        return promise.addListener(listener);
    }

    @Override
    public ChannelPromise addListeners(final GenericFutureListener<? extends Future<? super Void>>... listeners) {
        return promise.addListeners(listeners);
    }

    @Override
    public ChannelPromise removeListener(final GenericFutureListener<? extends Future<? super Void>> listener) {
        return promise.removeListener(listener);
    }

    @Override
    public ChannelPromise removeListeners(final GenericFutureListener<? extends Future<? super Void>>... listeners) {
        return promise.removeListeners(listeners);
    }

    @Override
    public ChannelPromise sync() throws InterruptedException {
        return promise.sync();
    }

    @Override
    public ChannelPromise syncUninterruptibly() {
        return promise.syncUninterruptibly();
    }

    @Override
    public ChannelPromise await() throws InterruptedException {
        return promise.await();
    }

    @Override
    public ChannelPromise awaitUninterruptibly() {
        return promise.awaitUninterruptibly();
    }

    @Override
    public boolean await(final long timeout, final TimeUnit unit) throws InterruptedException {
        return promise.await(timeout, unit);
    }

    @Override
    public boolean await(final long timeoutMillis) throws InterruptedException {
        return promise.await(timeoutMillis);
    }

    @Override
    public boolean awaitUninterruptibly(final long timeout, final TimeUnit unit) {
        return promise.awaitUninterruptibly(timeout, unit);
    }

    @Override
    public boolean awaitUninterruptibly(final long timeoutMillis) {
        return promise.awaitUninterruptibly(timeoutMillis);
    }

    @Override
    public Void getNow() {
        return promise.getNow();
    }

    @Override
    public boolean cancel(final boolean mayInterruptIfRunning) {
        return promise.cancel(mayInterruptIfRunning);
    }

    @Override
    public boolean isCancelled() {
        return promise.isCancelled();
    }

    @Override
    public boolean isDone() {
        return promise.isDone();
    }

    @Override
    public Void get() throws InterruptedException, ExecutionException {
        return promise.get();
    }

    @Override
    public Void get(final long timeout,
                    final TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return promise.get(timeout, unit);
    }

    @Override
    public boolean isVoid() {
        return promise.isVoid();
    }

    @Override
    public ChannelPromise unvoid() {
        return promise.unvoid();
    }

    @Override
    public String toString() {
        return "UserCallPromise{" +
                "userCall=" + userCall +
                ", channelPromise=" + promise +
                '}';
    }
}
