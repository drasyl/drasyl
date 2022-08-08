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
package org.drasyl.handler.dht.chord;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.concurrent.FutureListener;
import org.drasyl.handler.rmi.RmiClientHandler;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import static java.util.Objects.requireNonNull;
import static org.drasyl.handler.dht.chord.ChordUtil.chordId;
import static org.drasyl.handler.dht.chord.ChordUtil.chordIdHex;

/**
 * Joins the Chord distributed hash table.
 */
public class ChordJoinHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(ChordJoinHandler.class);
    private final ChordFingerTable fingerTable;
    private final DrasylAddress contact;

    public ChordJoinHandler(final ChordFingerTable fingerTable,
                            final DrasylAddress contact) {
        this.fingerTable = requireNonNull(fingerTable);
        this.contact = requireNonNull(contact);
    }

    /*
     * Handler Events
     */

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        if (ctx.channel().isActive()) {
            doJoinTask(ctx);
        }
    }

    /*
     * Channel Events
     */

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        doJoinTask(ctx);
        ctx.fireChannelActive();
    }

    /*
     * Join Task
     */

    private void doJoinTask(final ChannelHandlerContext ctx) {
        LOG.info("Join DHT ring by asking `{}` to find the successor for my id `{}`.", contact, chordIdHex(ctx.channel().localAddress()));
        final RmiClientHandler client = ctx.pipeline().get(RmiClientHandler.class);
        final ChordService service = client.lookup("ChordService", ChordService.class, contact);
        service.findSuccessor(chordId(ctx.channel().localAddress())).addListener((FutureListener<DrasylAddress>) future -> {
            if (future.isSuccess()) {
                final DrasylAddress successor = future.getNow();
                LOG.info("Successor for id `{}` is `{}`.", chordIdHex(ctx.channel().localAddress()), successor);
                LOG.info("Set `{}` as our successor.", successor);
                fingerTable.setSuccessor(successor, client).finish(ctx.executor());
            }
            else {
                LOG.error("Failed to join DHT ring `{}`:", contact, future.cause()); // FIXME: 60000ms eigentlich. wäre cool, wenn man das ändern könnte!
                ctx.pipeline().fireExceptionCaught(new ChordException("Failed to join DHT ring.", future.cause()));
                ctx.pipeline().close();
            }
        });
    }
}
