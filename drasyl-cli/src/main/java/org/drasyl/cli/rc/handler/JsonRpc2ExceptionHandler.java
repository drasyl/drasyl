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
package org.drasyl.cli.rc.handler;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import org.drasyl.cli.node.message.JsonRpc2Error;
import org.drasyl.cli.node.message.JsonRpc2Response;

import static io.netty.channel.ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE;
import static org.drasyl.cli.node.message.JsonRpc2Error.PARSE_ERROR;

@Sharable
public class JsonRpc2ExceptionHandler extends ChannelDuplexHandler {
    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx,
                                final Throwable cause) {
        if (cause instanceof DecoderException) {
            final JsonRpc2Error error = new JsonRpc2Error(PARSE_ERROR, "invalid JSON was received.");
            final JsonRpc2Response response = new JsonRpc2Response(error, "");
            ctx.writeAndFlush(response).addListener(FIRE_EXCEPTION_ON_FAILURE);
        }
        else {
            ctx.fireExceptionCaught(cause);
        }
    }
}
