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
package org.drasyl.cli.rc.channel;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.Delimiters;
import org.drasyl.cli.rc.handler.JsonRpc2ExceptionHandler;
import org.drasyl.cli.rc.handler.JsonRpc2RequestDecoder;
import org.drasyl.cli.rc.handler.JsonRpc2ResponeEncoder;

public abstract class RcJsonRpc2OverTcpServerInitializer extends ChannelInitializer<Channel> {
    @Override
    protected void initChannel(final Channel ch) throws Exception {
        final ChannelPipeline p = ch.pipeline();

        p.addLast(new JsonRpc2ResponeEncoder());
        p.addLast(new DelimiterBasedFrameDecoder(Integer.MAX_VALUE, true, Delimiters.lineDelimiter()));
        p.addLast(new JsonRpc2RequestDecoder());
        jsonRpc2RequestStage(p);
        p.addLast(new JsonRpc2ExceptionHandler());
    }

    protected abstract void jsonRpc2RequestStage(ChannelPipeline p);
}
