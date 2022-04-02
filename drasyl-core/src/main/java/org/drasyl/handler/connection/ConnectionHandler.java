/*
 * Copyright (c) 2020-2022 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.handler.connection;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.drasyl.util.ReferenceCountUtil;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;
import static org.drasyl.handler.connection.ConnectionHandler.State.CLOSED;
import static org.drasyl.handler.connection.ConnectionHandler.State.ESTABLISHED;
import static org.drasyl.handler.connection.ConnectionHandler.State.SYN_RECEIVED;
import static org.drasyl.handler.connection.ConnectionHandler.State.SYN_SENT;
import static org.drasyl.util.RandomUtil.randomInt;

public class ConnectionHandler extends SimpleChannelInboundHandler<ConnectionMessage> {
    private static final Logger LOG = LoggerFactory.getLogger(ConnectionHandler.class);

    static enum State {
        // connection does not exist
        CLOSED,
        // connection non-synchronized
        SYN_SENT,
        SYN_RECEIVED,
        // connection synchronized
        ESTABLISHED,
        FIN_WAIT_1,
        FIN_WAIT_2,
        CLOSE_WAIT,
        CLOSING,
        LAST_ACK,
        TIME_WAIT
    }

    private final Supplier<Integer> seqProvider;
    State state;
    int seq;
    int ack;

    public ConnectionHandler(final Supplier<Integer> seqProvider,
                             final State state,
                             final int seq,
                             final int ack) {
        this.seqProvider = requireNonNull(seqProvider);
        this.state = requireNonNull(state);
        this.seq = seq;
        this.ack = ack;
    }

    public ConnectionHandler() {
        this(() -> randomInt(Integer.MIN_VALUE, Integer.MAX_VALUE), CLOSED, 0, 0);
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        ctx.fireChannelActive();

        if (state == CLOSED) {
            // initiate synchronization
            seq = seqProvider.get();
            ctx.writeAndFlush(new Syn(seq));
            state = SYN_SENT;
        }
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx,
                                final ConnectionMessage msg) throws Exception {
        switch (state) {
            case SYN_SENT:
                if (msg instanceof Syn) {
                    state = SYN_RECEIVED;
                    ack = ((Syn) msg).seq() + 1;
                    ctx.writeAndFlush(new SynAck(seq, ack));
                }
                else if (msg instanceof Ack) {
                    // peer is already in a synchronized state, reset him...
                    ctx.writeAndFlush(new Rst(((Ack) msg).seq()));
                    // and try a new synchronization attempt
                    ctx.writeAndFlush(new Syn(seq));
                }
                else {
                    LOG.warn("Received unexpected message `{}` while beeing in `{}` state.", () -> msg.getClass().getSimpleName(), () -> state);
                    ctx.close();
                }
                break;

            case SYN_RECEIVED:
                if (msg instanceof SynAck && ((SynAck) msg).ack() == seq + 1) {
                    seq++;
                    state = ESTABLISHED;
                }
                else {
                    LOG.warn("Received unexpected message `{}` while beeing in `{}` state.", () -> msg.getClass().getSimpleName(), () -> state);
                    ctx.close();
                }
                break;

            case ESTABLISHED:
                if (msg instanceof Syn) {
                    // peer is not in a synchronized state, sent him what we exspect
                    ctx.writeAndFlush(new Ack(ack));
                }
                else if (msg instanceof Rst) {
                    // peer wants to reset connection
                    state = CLOSED;
                    ctx.close();
                }
                else {
                    LOG.warn("Received unexpected message `{}` while beeing in `{}` state.", () -> msg.getClass().getSimpleName(), () -> state);
                    ctx.close();
                }
                break;

            case CLOSED:
                // discard incoming message
                ReferenceCountUtil.release(msg);

            default:
                LOG.warn("Received unexpected message `{}` while beeing in `{}` state.", () -> msg.getClass().getSimpleName(), () -> state);
                ctx.close();
        }
    }
}
