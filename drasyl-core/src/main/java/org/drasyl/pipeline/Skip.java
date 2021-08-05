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

import org.drasyl.channel.MigrationHandlerContext;
import org.drasyl.event.Event;
import org.drasyl.pipeline.address.Address;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.CompletableFuture;

/**
 * Indicates that the annotated handler method ({@link Handler#onInbound(MigrationHandlerContext, Address, Object,
 * CompletableFuture)}, {@link Handler#onOutbound(MigrationHandlerContext, Address, Object, CompletableFuture)},
 * {@link Handler#onEvent(MigrationHandlerContext, Event, CompletableFuture)} or {@link
 * Handler#onException(MigrationHandlerContext, Exception)}) in {@link Handler} will not be invoked by
 * the {@link Pipeline} and so <strong>MUST</strong> only be used when the {@link Handler} method
 * does nothing except forward to the next {@link Handler} in the pipeline.
 * <p>
 * Note that this annotation is not {@linkplain Inherited inherited}. If a user overrides a method
 * annotated with {@link Skip}, it will not be skipped anymore. Similarly, the user can override a
 * method not annotated with {@link Skip} and simply pass the event through to the next handler,
 * which reverses the behavior of the supertype.
 * <b>The skippable method MUST be implemented always because some environments prohibit the
 * execution of reflections. In this case the method implementation is executed, also if it is
 * annotated with {@link Skip}</b>.
 * </p>
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Skip {
}
