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
package org.drasyl.handler.remote;

import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.drasyl.channel.InetAddressedMessage;
import org.drasyl.handler.remote.protocol.RemoteMessage;
import org.drasyl.identity.DrasylAddress;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.identity.Identity.POW_DIFFICULTY;
import static org.drasyl.util.Preconditions.requirePositive;

/**
 * This handler filters out all messages received with invalid proof of work.
 */
@SuppressWarnings("java:S110")
@Sharable
public final class InvalidProofOfWorkFilter extends SimpleChannelInboundHandler<InetAddressedMessage<RemoteMessage>> {
    private final Map<DrasylAddress, Long> senderCache;
    private final int maximumCacheSize;
    private final long expireCacheAfter;
    private long now;

    public InvalidProofOfWorkFilter() {
        this(100, 3_600_000L);
    }

    public InvalidProofOfWorkFilter(final int maximumCacheSize,
                                    final long expireCacheAfter) {
        super(false);
        this.maximumCacheSize = requirePositive(maximumCacheSize);
        this.expireCacheAfter = requirePositive(expireCacheAfter);
        this.senderCache = new HashMap<>();
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) throws Exception {
        if (ctx.channel().isActive()) {
            scheduleHousekeepingTask(ctx);
        }
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        ctx.fireChannelActive();
        scheduleHousekeepingTask(ctx);
    }

    @Override
    public boolean acceptInboundMessage(final Object msg) throws Exception {
        return msg instanceof InetAddressedMessage && ((InetAddressedMessage<?>) msg).content() instanceof RemoteMessage;
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx,
                                final InetAddressedMessage<RemoteMessage> msg) throws InvalidProofOfWorkException {
        final RemoteMessage remoteMsg = msg.content();
        final boolean passThroughMessage = !ctx.channel().localAddress().equals(remoteMsg.getRecipient()) || hasValidProofOfWork(remoteMsg);
        if (passThroughMessage) {
            ctx.fireChannelRead(msg);
        }
        else {
            msg.release();
            throw new InvalidProofOfWorkException(remoteMsg);
        }
    }

    private boolean hasValidProofOfWork(final RemoteMessage remoteMsg) {
        if (senderCache.containsKey(remoteMsg.getSender())) {
            return true;
        }
        else if (remoteMsg.getProofOfWork().isValid(remoteMsg.getSender(), POW_DIFFICULTY) && senderCache.size() < maximumCacheSize) {
            senderCache.put(remoteMsg.getSender(), now);
            return true;
        }
        return false;
    }

    private void scheduleHousekeepingTask(final ChannelHandlerContext ctx) {
        // requesting the time triggers a system call and is therefore considered to be expensive.
        // This is why we cache the current time
        now = System.currentTimeMillis();

        ctx.executor().schedule(() -> {
            // remove all entries from cache after "expireAfter"
            final Iterator<Entry<DrasylAddress, Long>> iterator = senderCache.entrySet().iterator();
            while (iterator.hasNext()) {
                final Entry<DrasylAddress, Long> entry = iterator.next();
                final long lastTime = entry.getValue();
                if (lastTime < now) {
                    iterator.remove();
                }
            }

            if (ctx.channel().isActive()) {
                scheduleHousekeepingTask(ctx);
            }
        }, expireCacheAfter, MILLISECONDS);
    }

    /**
     * Signals that a message was received with an invalid {@link org.drasyl.identity.ProofOfWork}
     * and was dropped.
     */
    public static class InvalidProofOfWorkException extends Exception {
        public InvalidProofOfWorkException(final RemoteMessage msg) {
            super("Message `" + msg.getNonce() + "` from `" + (msg.getSender() != null ? msg.getSender() : null) + "` with invalid proof of work (" + (msg.getProofOfWork() != null ? msg.getProofOfWork() : null) + ") dropped.");
        }
    }
}
