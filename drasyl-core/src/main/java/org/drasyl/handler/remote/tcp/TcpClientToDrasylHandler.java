/*
 * Copyright (c) 2020-2025 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.handler.remote.tcp;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.PlatformDependent;
import org.drasyl.channel.InetAddressedMessage;
import org.drasyl.channel.JavaDrasylChannel;
import org.drasyl.channel.JavaDrasylServerChannel;
import org.drasyl.handler.remote.PeersManager;
import org.drasyl.handler.remote.protocol.ApplicationMessage;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.internal.UnstableApi;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Objects.requireNonNull;

/**
 * This handler passes messages from the {@link io.netty.channel.socket.SocketChannel} to the
 * {@link JavaDrasylServerChannel}'s context.
 */
@UnstableApi
public class TcpClientToDrasylHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(TcpClientToDrasylHandler.class);
    private final JavaDrasylServerChannel parent;
    private final Queue<Object> outboundBuffer = PlatformDependent.newMpscQueue();
    private final Set<DrasylAddress> readCompletePending = ConcurrentHashMap.newKeySet();
    private ChannelHandlerContext ctx;
    private boolean parentReadCompletePending;

    public TcpClientToDrasylHandler(final JavaDrasylServerChannel parent) {
        this.parent = requireNonNull(parent);
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
        LOG.trace("Read `{}`", msg);

        if (msg instanceof InetAddressedMessage && ((InetAddressedMessage<?>) msg).content() instanceof ApplicationMessage && parent.localAddress().equals(((ApplicationMessage) ((InetAddressedMessage<?>) msg).content()).getRecipient())) {
            final PeersManager peersManager = parent.config().getPeersManager();

            final ApplicationMessage appMsg = (ApplicationMessage) ((InetAddressedMessage<?>) msg).content();
            peersManager.applicationMessageReceived(appMsg.getSender());

            final JavaDrasylChannel drasylChannel = parent.getChannel(appMsg.getSender());
            if (drasylChannel != null) {
                LOG.trace("{} Pass read to `{}` to `{}`.", ctx.channel(), msg, drasylChannel);
                drasylChannel.queueRead(appMsg.getPayload());
            }
            else {
                readCompletePending.add(appMsg.getSender());
                parent.serve(appMsg.getSender()).addListener(future -> {
                    final JavaDrasylChannel drasylChannel1 = (JavaDrasylChannel) future.get();
                    LOG.trace("{} Pass read to `{}` to `{}`.", ctx.channel(), msg, drasylChannel);
                    drasylChannel1.queueRead(appMsg.getPayload());
                });
            }
        }
        else {
            parentReadCompletePending = true;
            parent.pipeline().fireChannelRead(msg);
        }
    }

    @Override
    public void channelReadComplete(final ChannelHandlerContext ctx) {
        if (parentReadCompletePending) {
            parentReadCompletePending = false;
            parent.pipeline().fireChannelReadComplete();
        }
        for (final JavaDrasylChannel drasylChannel : parent.getChannels().values()) {
            if (drasylChannel.isRegistered()) {
                LOG.trace("{} Pass read complete to `{}`.", ctx.channel(), drasylChannel);
                drasylChannel.finishRead();
            }
        }
        for (final DrasylAddress address : readCompletePending) {
            parent.serve(address).addListener(future -> {
                final JavaDrasylChannel drasylChannel1 = (JavaDrasylChannel) future.get();
                LOG.trace("{} Pass read complete to `{}`.", ctx.channel(), drasylChannel1);
                drasylChannel1.finishRead();
            });
        }
        readCompletePending.clear();
        ctx.fireChannelReadComplete();
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        releaseOutboundBuffer();

        ctx.fireChannelInactive();
    }

    /**
     * This method places the message {@code o} in the queue for outbound messages to be written by
     * this channel. Queued message are not processed until {@link #finishWrite()} is called.
     */
    public void enqueueWrite(final Object o) {
        outboundBuffer.add(o);
    }

    /**
     * This method start processing (if any) queued outbound messages for the UDP channel. This
     * method ensures that read/write order is respected. Therefore, if UDP channel is currently
     * reading, these reads are performed first and the writes are performed afterwards.
     */
    public void finishWrite() {
        // check whether the channel is currently reading; if so, we must schedule the event in the
        // event loop to maintain the read/write order.
        if (ctx.executor().inEventLoop()) {
            finishWrite0(ctx);
        }
        else {
            runFinishWriteTask(ctx);
        }
    }

    private void finishWrite0(final ChannelHandlerContext ctx) {
        if (!outboundBuffer.isEmpty()) {
            writeOutbound(ctx);
        }

    }

    private void runFinishWriteTask(final ChannelHandlerContext ctx) {
        ctx.executor().execute(() -> this.finishWrite0(ctx));
    }

    void writeOutbound(final ChannelHandlerContext ctx) {
        final ChannelPipeline pipeline = ctx.pipeline();
        do {
            final Object o = outboundBuffer.poll();
            if (o == null) {
                break;
            }
            pipeline.write(o).addListener(future -> {
                if (!future.isSuccess()) {
                    LOG.warn("Outbound message `{}` written to socket channel failed:", () -> o, future::cause);
                }
            });
        } while (true); // TODO: use isWritable?

        // all messages written, fire flush event
        pipeline.flush();
    }

    private void releaseOutboundBuffer() {
        Object msg;
        while ((msg = outboundBuffer.poll()) != null) {
            ReferenceCountUtil.release(msg);
        }
    }
}
