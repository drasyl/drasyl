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
package org.drasyl.pipeline;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.FastThreadLocal;
import org.drasyl.event.Event;
import org.drasyl.pipeline.address.Address;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;

/**
 * Class to compute the mask of a given {@link Handler}. Inspired by the corresponding netty
 * implementation: https://github.com/netty/netty/blob/master/transport/src/main/java/io/netty/channel/ChannelHandlerMask.java
 */
public final class HandlerMask {
    // Using fast bitwise operations to compute if a method must be called
    public static final int ON_EVENT_MASK = 1;
    public static final int ON_EXCEPTION_MASK = 1 << 1;
    public static final int ON_INBOUND_MASK = 1 << 2;
    public static final int ON_OUTBOUND_MASK = 1 << 3;
    public static final int ALL = ON_EVENT_MASK |
            ON_EXCEPTION_MASK | ON_INBOUND_MASK | ON_OUTBOUND_MASK;
    private static final Logger LOG = LoggerFactory.getLogger(HandlerMask.class);
    private static final FastThreadLocal<Map<Class<? extends Handler>, Integer>> MASK_CACHE =
            new FastThreadLocal<>() {
                @Override
                protected Map<Class<? extends Handler>, Integer> initialValue() {
                    return new WeakHashMap<>(32);
                }
            };

    private HandlerMask() {
    }

    /**
     * Returns the mask for a given {@code handlerClass}.
     *
     * @param handlerClass the handler for which the mask should be returned
     * @return the handler mask
     */
    public static int mask(final Class<? extends Handler> handlerClass) {
        final Map<Class<? extends Handler>, Integer> cache = MASK_CACHE.get();
        Integer mask = cache.get(handlerClass);
        if (mask == null) {
            mask = calcMask(handlerClass);
            cache.put(handlerClass, mask);
        }

        return mask;
    }

    /**
     * Calculates the mask for the given {@code handlerClass}.
     *
     * @param handlerClass the handler for which the mask should be calculated
     * @return the handler mask
     */
    private static int calcMask(final Class<? extends Handler> handlerClass) {
        int mask = ALL;

        if (isSkippable(handlerClass, "onEvent",
                ChannelHandlerContext.class, Event.class, CompletableFuture.class)) {
            mask &= ~ON_EVENT_MASK;
        }

        if (isSkippable(handlerClass, "onException",
                ChannelHandlerContext.class, Exception.class)) {
            mask &= ~ON_EXCEPTION_MASK;
        }

        if (isSkippable(handlerClass, "onInbound",
                ChannelHandlerContext.class, Address.class, Object.class, CompletableFuture.class)) {
            mask &= ~ON_INBOUND_MASK;
        }

        if (isSkippable(handlerClass, "onOutbound",
                ChannelHandlerContext.class, Address.class, Object.class, CompletableFuture.class)) {
            mask &= ~ON_OUTBOUND_MASK;
        }

        return mask;
    }

    /**
     * This method checks if the given method {@code methodName} with the parameters {@code
     * paramTypes} has the {@link Skip} annotation.
     * <p>
     * If the current {@link SecurityManager} does not allow reflection, {@code false} is returned
     *
     * @param handlerClass the class of the handler
     * @param methodName   the method
     * @param paramTypes   the parameter types of {@code methodName}
     * @return if the given method {@code methodName} has the {@link Skip} annotation
     */
    @SuppressWarnings("java:S1905")
    static boolean isSkippable(final Class<? extends Handler> handlerClass,
                               final String methodName,
                               final Class<?>... paramTypes) {
        try {
            return AccessController.doPrivileged((PrivilegedExceptionAction<Boolean>) () -> {
                final Method m;
                try {
                    m = handlerClass.getMethod(methodName, paramTypes);
                }
                catch (final NoSuchMethodException e) {
                    LOG.trace("Class {} missing method {}, assume we can not skip execution", () -> handlerClass, () -> methodName);
                    return false;
                }

                return m != null && m.isAnnotationPresent(Skip.class); // avoid GraalVM error: https://github.com/netty/netty/commit/f48d9ad15f9379d808d6553ce18f99894a32cca5
            });
        }
        catch (final Exception e) { // NOSONAR
            return false;
        }
    }
}
