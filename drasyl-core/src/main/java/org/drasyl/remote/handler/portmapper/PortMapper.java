/*
 * Copyright (c) 2020-2021.
 *
 * This file is part of drasyl.
 *
 *  drasyl is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  drasyl is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.drasyl.remote.handler.portmapper;

import io.reactivex.rxjava3.disposables.Disposable;
import org.drasyl.event.Event;
import org.drasyl.event.NodeDownEvent;
import org.drasyl.event.NodeUnrecoverableErrorEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.skeleton.SimpleInboundHandler;
import org.drasyl.remote.protocol.AddressedByteBuf;
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
public class PortMapper extends SimpleInboundHandler<AddressedByteBuf, Address> {
    public static final String PORT_MAPPER = "PORT_MAPPER";
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
    public void eventTriggered(final HandlerContext ctx,
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
        ctx.fireEventTriggered(event, future);
    }

    @Override
    protected void matchedRead(final HandlerContext ctx,
                               final Address sender,
                               final AddressedByteBuf msg,
                               final CompletableFuture<Void> future) {
        if (methods.get(currentMethodPointer).acceptMessage(msg)) {
            future.complete(null);
            ctx.independentScheduler().scheduleDirect(() -> methods.get(currentMethodPointer).handleMessage(ctx, msg));
        }
        else {
            // message was not for the mapper -> passthrough
            ctx.fireRead(sender, msg, future);
        }
    }

    private void cycleNextMethod(final HandlerContext ctx, final NodeUpEvent event) {
        final int oldMethodPointer = currentMethodPointer;
        currentMethodPointer = (currentMethodPointer + 1) % methods.size();
        if (currentMethodPointer == 0) {
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
