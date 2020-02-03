/*
 * Copyright (c) 2020
 *
 * This file is part of drasyl.
 *
 * drasyl is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * drasyl is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.drasyl.all.handler;

import org.drasyl.all.messages.RelayException;
import org.drasyl.all.messages.Message;
import org.drasyl.all.messages.Response;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.ReferenceCountUtil;
import io.sentry.util.CircularFifoQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This handler filters incoming {@link Response} messages and only let them pass if they refer to an outgoing
 * {@link Message}.
 */
public class ResponseFilter extends SimpleChannelDuplexHandler<Response, Message> {
    private static final Logger LOG = LoggerFactory.getLogger(ResponseFilter.class);
    private final boolean compatibleMode;

    protected final CircularFifoQueue<String> outboundMessagesQueue;

    /**
     * Creates a new ResponseFilter with the given {@code limit} for the outgoing messages queue for this channel.
     *
     * @param limit the queue limit
     */
    public ResponseFilter(int limit) {
        this.outboundMessagesQueue = new CircularFifoQueue<>(limit);
        this.compatibleMode = false;
    }

    /**
     * Creates {@link ResponseFilter} that is compatible with the {@link DuplicateMessageFilter}.
     *
     * @param outboundMessagesQueue the <b>same</b> outbound queue as in {@link DuplicateMessageFilter}
     */
    public ResponseFilter(CircularFifoQueue<String> outboundMessagesQueue) {
        this.outboundMessagesQueue = outboundMessagesQueue;
        this.compatibleMode = true;
    }

    @Override
    protected void channelWrite0(ChannelHandlerContext ctx, Message msg) throws Exception {
        if (!outboundMessagesQueue.contains(msg.getMessageID()) && !compatibleMode)
            outboundMessagesQueue.add(msg.getMessageID());
        ctx.write(msg);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Response msg) throws Exception {
        if (outboundMessagesQueue.contains(msg.getMsgID())) {
            ctx.fireChannelRead(msg);
        } else {
            ctx.writeAndFlush(new Response<>(new RelayException("This response was not expected from us, as it does " +
                    "not refer to any valid request."),
                    msg.getMessageID()));
            ReferenceCountUtil.release(msg);
            LOG.debug("{} unexpected response was dropped.", msg);
        }
    }
}
