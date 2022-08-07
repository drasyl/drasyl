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
import org.drasyl.handler.rmi.message.RmiCancel;
import org.drasyl.handler.rmi.message.RmiRequest;
import org.drasyl.util.Pair;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.net.SocketAddress;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.util.Preconditions.requireNonNegative;

class RmiInvocationHandler implements InvocationHandler {
    private static final Logger LOG = LoggerFactory.getLogger(RmiInvocationHandler.class);
    private final RmiClientHandler handler;
    private final String name;
    private final SocketAddress address;
    private final long timeoutMillis;

    public RmiInvocationHandler(final RmiClientHandler handler,
                                final String name,
                                final SocketAddress address,
                                final long timeoutMillis) {
        this.handler = requireNonNull(handler);
        this.name = requireNonNull(name);
        this.address = requireNonNull(address);
        this.timeoutMillis = requireNonNegative(timeoutMillis);
    }

    @SuppressWarnings("unchecked")
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
            LOG.debug("Invoke `{}({})` of object `{}`.", method::getName, () -> Arrays.stream(method.getParameterTypes()).map(Class::getName).collect(Collectors.joining(",")), () -> proxy);

            final Promise<Object> promise = handler.ctx.executor().newPromise();
            try {
                final ByteBuf argsBuf = RmiUtil.marshalArgs(args, handler.ctx.alloc().buffer());
                performRemoteInvocation(method, promise, argsBuf);
            }
            catch (final IOException e) {
                promise.tryFailure(new IllegalArgumentException(e));
            }

            return promise;
        }
    }

    private void performRemoteInvocation(final Method method,
                                         final Promise<Object> promise,
                                         final ByteBuf argsBuf) {
        final RmiRequest request = RmiRequest.of(name.hashCode(), RmiUtil.computeMethodHash(method), argsBuf);
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
                final Class<?> type = (Class<?>) ((ParameterizedType) method.getGenericReturnType()).getActualTypeArguments()[0];
                handler.requests.put(Pair.of(address, request.getId()), Pair.of(promise, type));
                promise.addListener(f -> handler.requests.remove(Pair.of(address, request.getId()))); // removes from map when future is done
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

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final RmiInvocationHandler that = (RmiInvocationHandler) o;
        return Objects.equals(name, that.name) && Objects.equals(address, that.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, address);
    }

    @Override
    public String toString() {
        return name + "@" + address;
    }
}
