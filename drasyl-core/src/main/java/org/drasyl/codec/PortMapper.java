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
package org.drasyl.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.concurrent.ScheduledFuture;
import org.drasyl.event.Node;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.pipeline.address.InetSocketAddressWrapper;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static java.time.Duration.ofMinutes;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class PortMapper extends SimpleChannelInboundHandler<DatagramPacket> {
    public static final Duration MAPPING_LIFETIME = ofMinutes(10);
    public static final Duration RETRY_DELAY = ofMinutes(5);
    private static final Logger LOG = LoggerFactory.getLogger(PortMapper.class);
    private final ArrayList<PortMapping> methods;
    private int currentMethodPointer;
    private ScheduledFuture<?> retryTask;

    @SuppressWarnings("java:S2384")
    PortMapper(final ArrayList<PortMapping> methods,
               final int currentMethodPointer,
               final ScheduledFuture<?> retryTask) {
        super(false);
        this.methods = methods;
        this.currentMethodPointer = currentMethodPointer;
        this.retryTask = retryTask;
    }

    public PortMapper() {
        this(new ArrayList<>(List.of(new PcpPortMapping()/*, new NatPmpPortMapping(), new UpnpIgdPortMapping()*/)), 0, null);
    }

    @Override
    public void userEventTriggered(final ChannelHandlerContext ctx,
                                   final Object evt) throws Exception {
        if (evt instanceof UdpServer.UdpServerPort) {
            LOG.debug("Try to map port with method `{}`.", () -> methods.get(currentMethodPointer));
            final NodeUpEvent event = NodeUpEvent.of(Node.of(((DrasylServerChannel) ctx.channel()).localAddress0(), ((UdpServer.UdpServerPort) evt).getPort()));
            methods.get(currentMethodPointer).start(ctx, event, () -> cycleNextMethod(ctx, event));
        }

        // passthrough event
        super.userEventTriggered(ctx, evt);
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
    protected void channelRead0(final ChannelHandlerContext ctx,
                                final DatagramPacket addressedMsg) throws Exception {
        final ByteBuf msg = addressedMsg.content();
        final InetSocketAddressWrapper sender = new InetSocketAddressWrapper(addressedMsg.sender());

        if (methods.get(currentMethodPointer).acceptMessage(sender, msg)) {
            ctx.executor().execute(() -> methods.get(currentMethodPointer).handleMessage(ctx, sender, msg));
        }
        else {
            // message was not for the mapper -> passthrough
            ctx.fireChannelRead(addressedMsg);
        }
    }

    private void cycleNextMethod(final ChannelHandlerContext ctx, final NodeUpEvent event) {
        final int oldMethodPointer = currentMethodPointer;
        currentMethodPointer = (currentMethodPointer + 1) % methods.size();
        if (currentMethodPointer == 0) {
            //noinspection unchecked
            LOG.debug("Method `{}` was unable to create mapping. All methods have failed. Wait {}s and then give next method `{}` a try.", () -> methods.get(oldMethodPointer), RETRY_DELAY::toSeconds, () -> methods.get(currentMethodPointer));
            retryTask = ctx.executor().schedule(() -> {
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
