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
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.concurrent.Future;
import org.drasyl.channel.AddressedMessage;
import org.drasyl.pipeline.address.InetSocketAddressWrapper;
import org.drasyl.remote.handler.UdpServer;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static java.time.Duration.ofMinutes;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * This handler tries to create a port forwarding for the UDP server using different methods (PCP,
 * NAT-PMP, UPnP-IGD, etc.). For this purpose, the individual methods are tried one after one. If
 * all methods fail, the program waits for {@link #RETRY_DELAY} and then tries all methods again. It
 * never gives up.
 */
public class PortMapper extends SimpleChannelInboundHandler<AddressedMessage<?, ?>> {
    public static final Duration MAPPING_LIFETIME = ofMinutes(10);
    public static final Duration RETRY_DELAY = ofMinutes(5);
    private static final Logger LOG = LoggerFactory.getLogger(PortMapper.class);
    private final ArrayList<PortMapping> methods;
    private int currentMethodPointer;
    private Future retryTask;

    @SuppressWarnings("java:S2384")
    PortMapper(final ArrayList<PortMapping> methods,
               final int currentMethodPointer,
               final Future retryTask) {
        this.methods = methods;
        this.currentMethodPointer = currentMethodPointer;
        this.retryTask = retryTask;
    }

    public PortMapper() {
        this(new ArrayList<>(List.of(new PcpPortMapping(), new NatPmpPortMapping(), new UpnpIgdPortMapping())), 0, null);
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx,
                                final AddressedMessage<?, ?> msg) throws Exception {
        if (msg.message() instanceof ByteBuf && msg.address() instanceof InetSocketAddressWrapper) {
            if (methods.get(currentMethodPointer).acceptMessage((InetSocketAddressWrapper) msg.address(), (ByteBuf) msg.message())) {
                ctx.executor().execute(() -> methods.get(currentMethodPointer).handleMessage(ctx, (InetSocketAddressWrapper) msg.address(), (ByteBuf) msg.message()));
            }
            else {
                // message was not for the mapper -> passthrough
                ctx.fireChannelRead(msg);
            }
        }
        else {
            ctx.fireChannelRead(msg);
        }
    }

    private void cycleNextMethod(final ChannelHandlerContext ctx, final int port) {
        final int oldMethodPointer = currentMethodPointer;
        currentMethodPointer = (currentMethodPointer + 1) % methods.size();
        if (currentMethodPointer == 0) {
            //noinspection unchecked
            LOG.debug("Method `{}` was unable to create mapping. All methods have failed. Wait {}s and then give next method `{}` a try.", () -> methods.get(oldMethodPointer), RETRY_DELAY::toSeconds, () -> methods.get(currentMethodPointer));
            retryTask = ctx.executor().schedule(() -> {
                LOG.debug("Try to map port with method `{}`.", () -> methods.get(currentMethodPointer));
                methods.get(currentMethodPointer).start(ctx, port, () -> cycleNextMethod(ctx, port));
            }, RETRY_DELAY.toMillis(), MILLISECONDS);
        }
        else {
            LOG.debug("Method `{}` was unable to create mapping. Let's give next method `{}` a try.", () -> methods.get(oldMethodPointer), () -> methods.get(currentMethodPointer));
            methods.get(currentMethodPointer).start(ctx, port, () -> cycleNextMethod(ctx, port));
        }
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
        if (retryTask != null) {
            retryTask.cancel(false);
            retryTask = null;
        }
        methods.get(currentMethodPointer).stop(ctx);

        super.channelInactive(ctx);
    }

    @Override
    public void userEventTriggered(final ChannelHandlerContext ctx,
                                   final Object evt) {
        if (evt instanceof UdpServer.Port) {
            LOG.debug("Try to map port with method `{}`.", () -> methods.get(currentMethodPointer));
            methods.get(currentMethodPointer).start(ctx, ((UdpServer.Port) evt).getPort(), () -> cycleNextMethod(ctx, ((UdpServer.Port) evt).getPort()));
        }

        ctx.fireUserEventTriggered(evt);
    }
}
