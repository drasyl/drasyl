/*
 * Copyright (c) 2020-2023 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.cli.sdo.handler;

import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.drasyl.cli.sdo.event.SdoMessageReceived;
import org.drasyl.cli.sdo.message.SdoMessage;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

@Sharable
public class SdoMessageChildHandler extends SimpleChannelInboundHandler<SdoMessage> {
    private static final Logger LOG = LoggerFactory.getLogger(SdoMessageChildHandler.class);

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx,
                                final SdoMessage msg) {
        final SdoMessageReceived evt = new SdoMessageReceived((DrasylAddress) ctx.channel().remoteAddress(), msg);
        ctx.channel().parent().pipeline().fireUserEventTriggered(evt);

//        if (msg instanceof JoinNetwork) {
//            // verify sender is network node
//            if (config.isNode((DrasylAddress) ctx.channel().remoteAddress())) {
//                LOG.debug("`{}` joined our network.", ctx.channel().remoteAddress());
//                ctx.writeAndFlush(new PushConfig(config.toString())).addListener(FIRE_EXCEPTION_ON_FAILURE);
//            }
//            else {
//                LOG.error("Got JoinNetwork from non-network node `{}`.", ctx.channel().remoteAddress());
//                ctx.writeAndFlush(new NetworkJoinDenied()).addListener(FIRE_EXCEPTION_ON_FAILURE);
//            }
//        }
    }
}
