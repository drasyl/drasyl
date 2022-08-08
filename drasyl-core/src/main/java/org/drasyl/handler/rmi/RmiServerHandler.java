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
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import org.drasyl.handler.rmi.message.RmiCancel;
import org.drasyl.handler.rmi.message.RmiError;
import org.drasyl.handler.rmi.message.RmiMessage;
import org.drasyl.handler.rmi.message.RmiRequest;
import org.drasyl.handler.rmi.message.RmiResponse;
import org.drasyl.util.Pair;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static java.util.Objects.requireNonNull;
import static org.drasyl.handler.rmi.RmiUtil.marshalResult;

/**
 * A {@link io.netty.channel.ChannelHandler} that can serve local object whose methods then can be
 * invoked by remote nodes using {@link RmiClientHandler}.
 *
 * @see RmiClientHandler
 */
public class RmiServerHandler extends SimpleChannelInboundHandler<AddressedEnvelope<RmiMessage, SocketAddress>> {
    private static final Logger LOG = LoggerFactory.getLogger(RmiServerHandler.class);
    private static final Map<Class<?>, Optional<Field>> callerFields = new HashMap<>();
    private final Map<Integer, Object> bindings;
    private final Map<Integer, Map<Integer, Method>> bindingsMethods;
    private final Map<Pair<SocketAddress, UUID>, Future<?>> invocations;

    public RmiServerHandler(final Map<Integer, Object> bindings,
                            final Map<Integer, Map<Integer, Method>> bindingsMethods,
                            final Map<Pair<SocketAddress, UUID>, Future<?>> invocations) {
        super(false);
        this.bindings = requireNonNull(bindings);
        this.bindingsMethods = requireNonNull(bindingsMethods);
        this.invocations = requireNonNull(invocations);
    }

    public RmiServerHandler() {
        this(new HashMap<>(), new HashMap<>(), new HashMap<>());
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean acceptInboundMessage(final Object msg) throws Exception {
        return msg instanceof AddressedEnvelope && (((AddressedEnvelope<?, SocketAddress>) msg).content() instanceof RmiRequest || ((AddressedEnvelope<?, SocketAddress>) msg).content() instanceof RmiCancel);
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx,
                                final AddressedEnvelope<RmiMessage, SocketAddress> msg) {
        LOG.debug("Got `{}`.", msg);
        if (msg.content() instanceof RmiRequest) {
            handleRequest(ctx, (RmiRequest) msg.content(), msg.sender());
        }
        else if (msg.content() instanceof RmiCancel) {
            handleCancel((RmiCancel) msg.content(), msg.sender());
        }
        else {
            ctx.fireChannelRead(msg);
        }
    }

    private void handleRequest(final ChannelHandlerContext ctx,
                               final RmiRequest request,
                               final SocketAddress caller) {
        final UUID id = request.getId();
        final int name = request.getName();
        final int methodHash = request.getMethod();
        final ByteBuf argsBuf = request.getArguments();

        // get binding
        final Object binding = bindings.get(name);

        if (binding == null) {
            request.release();
            replyError(ctx, caller, id, new RmiException("Binding not found."));
            return;
        }

        final Map<Integer, Method> bindingMethods = bindingsMethods.get(name);
        final Method method = bindingMethods.get(methodHash);

        if (method == null) {
            request.release();
            replyError(ctx, caller, id, new RmiException("Method not found."));
            return;
        }

        // invoke
        try {
            final Object[] args = RmiUtil.unmarshalArgs(method.getParameterTypes(), argsBuf);
            invokeMethod(ctx, caller, id, binding, method, args);
        }
        catch (final IOException e) {
            request.release();
            replyError(ctx, caller, id, e);
        }
    }

    private void handleCancel(final RmiCancel cancel, final SocketAddress sender) {
        final UUID id = cancel.getId();
        final Future<?> invocation = invocations.get(Pair.of(sender, id));
        if (invocation != null) {
            invocation.cancel(false);
        }
    }

    @SuppressWarnings({ "java:S3011", "java:S3776" })
    private void invokeMethod(final ChannelHandlerContext ctx,
                              final SocketAddress caller,
                              final UUID id,
                              final Object binding,
                              final Method method,
                              final Object[] args) {
        try {
            final Field callerField = getCallerField(binding.getClass());
            if (callerField != null) {
                callerField.set(binding, caller);
            }

            final Object result = method.invoke(binding, args);
            if (result instanceof Future) {
                invocations.put(Pair.of(caller, id), (Future<?>) result);
                ((Future<?>) result).addListener((FutureListener<Object>) future -> {
                    invocations.remove(Pair.of(caller, id));
                    if (future.isSuccess()) {
                        try {
                            final AddressedEnvelope<RmiResponse, SocketAddress> response = new DefaultAddressedEnvelope<>(RmiResponse.of(id, marshalResult(future.getNow(), ctx.alloc().buffer())), caller);
                            LOG.debug("Send `{}`.", response);
                            ctx.writeAndFlush(response).addListener((ChannelFutureListener) future2 -> {
                                if (future.cause() != null) {
                                    LOG.debug("Error", future.cause());
                                }
                            });
                        }
                        catch (final IOException e) {
                            replyError(ctx, caller, id, e);
                        }
                    }
                    else {
                        replyError(ctx, caller, id, future.cause());
                    }
                });
            }
        }
        catch (final InvocationTargetException | IllegalAccessException |
                     IllegalArgumentException e) {
            replyError(ctx, caller, id, e);
        }
    }

    /**
     * Binds a {@code object} to the specified {@code name} and makes it therefore available for
     * remote method invocation.
     *
     * @param name   name to bind the given object to
     * @param object an object to be made available for remote invocations
     * @throws IllegalArgumentException if {@code name} is already bound or {@code object} does not
     *                                  implement any interfaces
     * @throws NullPointerException     if {@code name} or {@code object} is {@code null}
     */
    public void bind(final String name, final Object object) {
        final int bindingKey = name.hashCode();

        if (bindings.containsKey(bindingKey)) {
            throw new IllegalArgumentException("`" + name + "` has already an associated binding.");
        }

        final Class<?> clazz = object.getClass();
        final Class<?>[] interfaces = clazz.getInterfaces();
        if (interfaces.length == 0) {
            throw new IllegalArgumentException("Given object did not implement any interfaces whose methods can be made available for remote invocations.");
        }

        final Map<Integer, Method> bindingMethods = new HashMap<>();
        for (final Class<?> iface : interfaces) {
            for (final Method method : iface.getMethods()) {
                final int methodHash = RmiUtil.computeMethodHash(method);
                bindingMethods.put(methodHash, method);
            }
        }

        LOG.debug("Bound `{}`: {}", name, object);
        bindings.put(bindingKey, object);
        bindingsMethods.put(bindingKey, bindingMethods);
    }

    /**
     * Removes the binding (if any) for the specified {@code name}.
     *
     * @param name name of the binding to delete
     * @throws NullPointerException if {@code name} is {@code null}
     */
    public void unbind(final String name) {
        final int bindingKey = name.hashCode();

        LOG.debug("Unbound `{}`", name);
        bindings.remove(bindingKey);
        bindingsMethods.remove(bindingKey);
    }

    /**
     * Replaces the binding (if any) for the specified {@code name} and makes {@code object}
     * available for remote method invocation.
     *
     * @param name   name of the binding to replace
     * @param object (new) object to be made available for remote invocations
     * @throws NullPointerException if {@code name} or {@code object} is {@code null}
     */
    public void rebind(final String name, final Object object) {
        unbind(name);
        bind(name, object);
    }

    private static void replyError(final ChannelHandlerContext ctx,
                                   final SocketAddress recipient,
                                   final UUID id,
                                   final Throwable cause) {
        final RmiError response = RmiError.of(id, cause);
        final AddressedEnvelope<RmiError, SocketAddress> msg = new DefaultAddressedEnvelope<>(response, recipient);
        LOG.debug("Send `{}`.", msg);
        ctx.writeAndFlush(msg).addListener((ChannelFutureListener) future -> {
            if (future.cause() != null) {
                LOG.debug("Error", future.cause());
            }
        });
    }

    @SuppressWarnings("java:S3011")
    private static Field getCallerField(final Class<?> clazz) {
        return callerFields.computeIfAbsent(clazz, key -> {
            final Field[] fields = clazz.getDeclaredFields();
            for (Field field : fields) {
                if (field.isAnnotationPresent(RmiCaller.class)) {
                    field.setAccessible(true);
                    return Optional.of(field);
                }
            }
            return Optional.empty();
        }).orElse(null);
    }
}
