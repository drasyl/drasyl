/*
 * Copyright (c) 2020
 *
 * This file is part of Relayserver.
 *
 * Relayserver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Relayserver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Relayserver.  If not, see <http://www.gnu.org/licenses/>.
 */

package city.sane.relay.common.handler;

import city.sane.relay.common.messages.RelayException;
import city.sane.relay.common.messages.ForwardableMessage;
import city.sane.relay.common.messages.Message;
import city.sane.relay.common.messages.Response;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.ReferenceCountUtil;
import io.sentry.util.CircularFifoQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Filters already seen {@link Message}s and drops them.
 */
public class DuplicateMessageFilter extends SimpleChannelDuplexHandler<Message, Message> {
    private static final Logger LOG = LoggerFactory.getLogger(DuplicateMessageFilter.class);
    private final CircularFifoQueue<String> outboundMessagesQueue;
    private final CircularFifoQueue<String> inboundMessagesQueue;

    /**
     * Creates a new {@link DuplicateMessageFilter} with in- and outbound messages queues of the given {@code limit}.
     *
     * @param limit limit for queues
     */
    public DuplicateMessageFilter(int limit) {
        this(new CircularFifoQueue<>(limit), new CircularFifoQueue<>(limit));
    }

    /**
     * Creates a new {@link DuplicateMessageFilter} that allows to share the {@code
     * outboundMessagesQueue}.
     *
     * @param outboundMessagesQueue message queue for outbound messages
     * @param limit                 limit for {@link #inboundMessagesQueue}
     */
    public DuplicateMessageFilter(CircularFifoQueue<String> outboundMessagesQueue, int limit) {
        this(new CircularFifoQueue<>(limit), outboundMessagesQueue);
    }

    /**
     * Creates a new {@link DuplicateMessageFilter} that allows to share the {@code inboundMessagesQueue} and {@code
     * outboundMessagesQueue}.
     *
     * @param inboundMessagesQueue  message queue for inbound messages
     * @param outboundMessagesQueue message queue for outbound messages
     */
    public DuplicateMessageFilter(CircularFifoQueue<String> inboundMessagesQueue,
                                  CircularFifoQueue<String> outboundMessagesQueue) {
        this.inboundMessagesQueue = inboundMessagesQueue;
        this.outboundMessagesQueue = outboundMessagesQueue;
    }

    @Override
    protected void channelWrite0(ChannelHandlerContext ctx, Message msg) throws Exception {
        if (!(msg instanceof ForwardableMessage) && outboundMessagesQueue.contains(msg.getMessageID())) {
            LOG.debug("{} was already seen and was dropped.", msg);
            ReferenceCountUtil.release(msg);
            // is visible to the listening futures
            throw new IllegalArgumentException("This message was already send.");
        } else {
            outboundMessagesQueue.add(msg.getMessageID());
            ctx.write(msg);
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message msg) throws Exception {
        if (inboundMessagesQueue.contains(msg.getMessageID())) {
            ctx.writeAndFlush(new Response(new RelayException("This message was already send."), msg.getMessageID()));
            ReferenceCountUtil.release(msg);
            LOG.debug("{} was already seen and was dropped.", msg);
        } else {
            inboundMessagesQueue.add(msg.getMessageID());
            ctx.fireChannelRead(msg);
        }
    }
}
