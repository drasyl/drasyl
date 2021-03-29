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
package org.drasyl.remote.handler.portmapper;

import io.netty.buffer.ByteBuf;
import io.reactivex.rxjava3.disposables.Disposable;
import org.drasyl.event.Event;
import org.drasyl.event.NodeDownEvent;
import org.drasyl.event.NodeUnrecoverableErrorEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.address.InetSocketAddressWrapper;
import org.drasyl.pipeline.skeleton.SimpleInboundHandler;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static java.time.Duration.ofMinutes;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * This handler tries to create a port forwarding for the UDP server using different methods (PCP,
 * NAT-PMP, UPnP-IGD, etc.). For this purpose, the individual methods are tried one after one. If
 * all methods fail, the program waits for {@link #RETRY_DELAY} and then tries all methods again. It
 * never gives up.
 */
public class PortMapper extends SimpleInboundHandler<ByteBuf, InetSocketAddressWrapper> {
    public static final Duration MAPPING_LIFETIME = ofMinutes(10);
    public static final Duration RETRY_DELAY = ofMinutes(5);
    private static final Logger LOG = LoggerFactory.getLogger(PortMapper.class);
    private final ArrayList<PortMapping> methods;
    private int currentMethodPointer;
    private Disposable retryTask;

    @SuppressWarnings("java:S2384")
    PortMapper(final ArrayList<PortMapping> methods,
               final int currentMethodPointer,
               final Disposable retryTask) {
        this.methods = methods;
        this.currentMethodPointer = currentMethodPointer;
        this.retryTask = retryTask;
    }

    public PortMapper() {
        this(new ArrayList<>(List.of(new PcpPortMapping(), new NatPmpPortMapping(), new UpnpIgdPortMapping())), 0, null);
    }

    @Override
    public void onEvent(final HandlerContext ctx,
                        final Event event,
                        final CompletableFuture<Void> future) {
        if (event instanceof NodeUpEvent) {
            LOG.debug("Try to map port with method `{}`.", () -> methods.get(currentMethodPointer));
            methods.get(currentMethodPointer).start(ctx, (NodeUpEvent) event, () -> cycleNextMethod(ctx, (NodeUpEvent) event));
        }
        else if (event instanceof NodeUnrecoverableErrorEvent || event instanceof NodeDownEvent) {
            if (retryTask != null) {
                retryTask.dispose();
                retryTask = null;
            }
            methods.get(currentMethodPointer).stop(ctx);
        }

        // passthrough event
        ctx.passEvent(event, future);
    }

    @Override
    protected void matchedInbound(final HandlerContext ctx,
                                  final InetSocketAddressWrapper sender,
                                  final ByteBuf msg,
                                  final CompletableFuture<Void> future) {
        if (methods.get(currentMethodPointer).acceptMessage(sender, msg)) {
            future.complete(null);
            ctx.independentScheduler().scheduleDirect(() -> methods.get(currentMethodPointer).handleMessage(ctx, sender, msg));
        }
        else {
            // message was not for the mapper -> passthrough
            ctx.passInbound(sender, msg, future);
        }
    }

    private void cycleNextMethod(final HandlerContext ctx, final NodeUpEvent event) {
        final int oldMethodPointer = currentMethodPointer;
        currentMethodPointer = (currentMethodPointer + 1) % methods.size();
        if (currentMethodPointer == 0) {
            //noinspection unchecked
            LOG.debug("Method `{}` was unable to create mapping. All methods have failed. Wait {}s and then give next method `{}` a try.", () -> methods.get(oldMethodPointer), RETRY_DELAY::toSeconds, () -> methods.get(currentMethodPointer));
            retryTask = ctx.independentScheduler().scheduleDirect(() -> {
                LOG.debug("Try to map port with method `{}`.", () -> methods.get(currentMethodPointer));
                methods.get(currentMethodPointer).start(ctx, event, () -> cycleNextMethod(ctx, event));
            }, RETRY_DELAY.toMillis(), MILLISECONDS);
        }
        else {
            LOG.debug("Method `{}` was unable to create mapping. Let's give next method `{}` a try.", () -> methods.get(oldMethodPointer), () -> methods.get(currentMethodPointer));
            methods.get(currentMethodPointer).start(ctx, event, () -> cycleNextMethod(ctx, event));
        }
    }
}
