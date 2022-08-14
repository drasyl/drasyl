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
package org.drasyl.handler.rmi;

import io.netty.buffer.ByteBuf;
import io.netty.channel.AddressedEnvelope;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.DefaultAddressedEnvelope;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.StringUtil;
import org.drasyl.handler.rmi.annotation.RmiResultCache;
import org.drasyl.handler.rmi.annotation.RmiTimeout;
import org.drasyl.handler.rmi.message.RmiCancel;
import org.drasyl.handler.rmi.message.RmiRequest;
import org.drasyl.util.ExpiringMap;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.net.SocketAddress;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.handler.rmi.RmiUtil.computeMethodHash;
import static org.drasyl.handler.rmi.RmiUtil.marshalArgs;
import static org.drasyl.handler.rmi.RmiUtil.unmarshalResult;
import static org.drasyl.handler.rmi.annotation.RmiTimeout.DEFAULT_INVOCATION_TIMEOUT;

class RmiInvocationHandler implements InvocationHandler {
    private static final Logger LOG = LoggerFactory.getLogger(RmiInvocationHandler.class);
    private static final Map<Method, Long> methodTimeouts = new HashMap<>();
    private static final Map<Method, Long> methodResultCaches = new HashMap<>();
    private final Class<?> clazz;
    private final RmiClientHandler handler;
    private final String name;
    private final SocketAddress address;
    private final Map<Method, Map<Integer, Optional<Object>>> resultsCache = new HashMap<>();
    private final Map<UUID, RemoteInvocation> requests = new HashMap<>();

    public RmiInvocationHandler(final RmiClientHandler handler,
                                final Class<?> clazz,
                                final String name,
                                final SocketAddress address) {
        this.handler = requireNonNull(handler);
        this.clazz = requireNonNull(clazz);
        this.name = requireNonNull(name);
        this.address = requireNonNull(address);
    }

    @SuppressWarnings({ "unchecked", "OptionalAssignedToNull", "java:S2789", "java:S3776" })
    @Override
    public Object invoke(final Object proxy, final Method method, final Object[] args) {
        if (args != null && args.length == 1 && "equals".equals(method.getName())) {
            return equals(args[0]);
        }
        else if ((args == null || args.length == 0) && "hashCode".equals(method.getName())) {
            return hashCode();
        }
        else if ((args == null || args.length == 0) && "toString".equals(method.getName())) {
            return toString();
        }
        else if (method.getReturnType() != void.class && method.getReturnType() != Future.class) {
            throw new IllegalStateException("Method `" + method + "` must have return type `void` or `" + Future.class.getName() + "`.");
        }
        else if (handler.ctx == null) {
            throw new IllegalStateException("You have to add " + StringUtil.simpleClassName(handler) + " to the channel pipeline first.");
        }
        else {
            // cached result?
            final int cacheKey = Objects.hashCode(args);
            final Optional<Object> cachedResult = getCachedResult(method, cacheKey);

            if (cachedResult != null) {
                LOG.debug("Reuse cached result for invocation `{}({})` of remote object `{}`.", method::getName, () -> Arrays.stream(method.getParameterTypes()).map(Class::getName).collect(Collectors.joining(",")), () -> proxy);
                return handler.ctx.executor().newSucceededFuture(cachedResult.orElse(null));
            }

            // perform remote invocation
            LOG.debug("Invoke `{}({})` of remote object `{}`.", method::getName, () -> Arrays.stream(method.getParameterTypes()).map(Class::getName).collect(Collectors.joining(",")), () -> proxy);
            final Promise<Object> promise = handler.ctx.executor().newPromise();
            try {
                final ByteBuf argsBuf = marshalArgs(args, handler.ctx.alloc().buffer());
                performRemoteInvocation(method, promise, argsBuf, cacheKey);
            }
            catch (final IOException e) {
                promise.tryFailure(new IllegalArgumentException(e));
            }

            return promise;
        }
    }

    private void performRemoteInvocation(final Method method,
                                         final Promise<Object> promise,
                                         final ByteBuf argsBuf,
                                         final int cacheKey) {
        final RmiRequest request = RmiRequest.of(name.hashCode(), computeMethodHash(method), argsBuf);
        final AddressedEnvelope<RmiRequest, SocketAddress> msg = new DefaultAddressedEnvelope<>(request, address);
        LOG.debug("Send `{}`.", msg);
        final ChannelHandlerContext ctx = handler.ctx;
        ctx.writeAndFlush(msg).addListener((ChannelFutureListener) future -> {
            if (future.cause() != null) {
                LOG.debug("Error", future.cause());
                promise.tryFailure(future.cause());
            }
            else if (future.isCancelled()) {
                promise.cancel(false);
            }
            else if (method.getReturnType() == void.class) {
                promise.trySuccess(null);
            }
            else {
                // wait for result
                requests.put(request.getId(), new RemoteInvocation(method, promise, cacheKey));
                handler.requests.put(request.getId(), this);
                promise.addListener(f -> {
                    handler.requests.remove(request.getId());
                    requests.remove(request.getId());
                });

                // timeout?
                final long timeoutMillis = getMethodTimeout(method);
                if (timeoutMillis > 0) {
                    ctx.executor().schedule(() -> promise.tryFailure(new RmiException("Timeout! Got no response within " + timeoutMillis + "ms.")), timeoutMillis, MILLISECONDS);
                }
            }
        });
        promise.addListener((FutureListener<Object>) future -> {
            if (future.isCancelled()) {
                final RmiCancel cancel = RmiCancel.of(request.getId());
                final AddressedEnvelope<RmiCancel, SocketAddress> msg1 = new DefaultAddressedEnvelope<>(cancel, address);
                LOG.debug("Send `{}`.", msg1);
                ctx.writeAndFlush(msg1);
            }
        });
    }

    public void handleResult(final UUID id, final ByteBuf buf) {
        final RemoteInvocation invocation = requests.remove(id);
        if (invocation != null) {
            final Promise<Object> promise = invocation.getPromise();
            final Method method = invocation.getMethod();
            final Class<?> resultType = invocation.getReturnType();
            final int cacheKey = invocation.getCacheKey();

            try {
                final Object result = unmarshalResult(resultType, buf);
                putCachedResult(method, cacheKey, result);
                promise.trySuccess(result);
            }
            catch (final IOException e) {
                promise.tryFailure(e);
            }
        }
    }

    void handleError(final UUID id, final String message) {
        final RemoteInvocation invocation = requests.remove(id);
        if (invocation != null) {
            final Promise<Object> promise = invocation.getPromise();
            promise.setFailure(new RmiException(message));
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || !clazz.isAssignableFrom(o.getClass())) {
            return false;
        }
        return hashCode() == o.hashCode();
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, address);
    }

    @Override
    public String toString() {
        return name + "@" + address;
    }

    public SocketAddress getAddress() {
        return address;
    }

    private Optional<Object> getCachedResult(final Method method, final int key) {
        final long expirationTime = getMethodResultCache(method);
        if (expirationTime > 0) {
            final Map<Integer, Optional<Object>> resultCache = resultsCache.computeIfAbsent(method, m -> new ExpiringMap<>(1000, expirationTime, 0));
            return resultCache.get(key);
        }
        else {
            return null;
        }
    }

    private void putCachedResult(final Method method,
                                 final int key,
                                 final Object result) {
        final long expirationTime = getMethodResultCache(method);
        if (expirationTime > 0) {
            final Map<Integer, Optional<Object>> resultCache = resultsCache.computeIfAbsent(method, m -> new ExpiringMap<>(1000, expirationTime, 0));
            resultCache.put(key, Optional.ofNullable(result));
        }
    }

    private static synchronized long getMethodResultCache(final Method method) {
        return methodResultCaches.computeIfAbsent(method, m -> {
            final RmiResultCache annotation = getMethodAnnotation(RmiResultCache.class, m);
            if (annotation != null) {
                return annotation.value();
            }
            else {
                return 0L;
            }
        });
    }

    private static synchronized long getMethodTimeout(final Method method) {
        return methodTimeouts.computeIfAbsent(method, m -> {
            final RmiTimeout annotation = getMethodAnnotation(RmiTimeout.class, m);
            if (annotation != null) {
                return annotation.value();
            }
            else {
                return DEFAULT_INVOCATION_TIMEOUT;
            }
        });
    }

    private static <T extends Annotation> T getMethodAnnotation(final Class<T> annotation,
                                                                final Method method) {
        if (method.isAnnotationPresent(annotation)) {
            return method.getAnnotation(annotation);
        }
        else if (method.getDeclaringClass().isAnnotationPresent(annotation)) {
            return method.getDeclaringClass().getAnnotation(annotation);
        }
        else {
            return null;
        }
    }

    private static class RemoteInvocation {
        private final Promise<Object> promise;
        private final Method method;
        private final int cacheKey;

        public RemoteInvocation(final Method method,
                                final Promise<Object> promise,
                                final int cacheKey) {
            this.promise = requireNonNull(promise);
            this.method = requireNonNull(method);
            this.cacheKey = cacheKey;
        }

        public Promise<Object> getPromise() {
            return promise;
        }

        public Method getMethod() {
            return method;
        }

        public Class<?> getReturnType() {
            return (Class<?>) ((ParameterizedType) this.method.getGenericReturnType()).getActualTypeArguments()[0];
        }

        public int getCacheKey() {
            return cacheKey;
        }
    }
}
