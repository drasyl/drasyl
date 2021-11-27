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
package org.drasyl.cli.tunnel.message;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelId;
import io.netty.util.ReferenceCounted;

import static java.util.Objects.requireNonNull;

public class Write implements TunnelMessage, ReferenceCounted {
    private final String channelId;
    private final ByteBuf msg;

    public Write(final String channelId, final ByteBuf msg) {
        this.channelId = requireNonNull(channelId);
        this.msg = requireNonNull(msg);
    }

    public Write(final ChannelId channelId, final ByteBuf msg) {
        this(channelId.asLongText(), msg);
    }

    public Write(final Channel channel, final ByteBuf msg) {
        this(channel.id(), msg);
    }

    @Override
    public String toString() {
        return "Write{" +
                "channelId='" + channelId + '\'' +
                ", msg=bytes[" + msg.readableBytes() + ']' +
                '}';
    }

    public String getChannelId() {
        return channelId;
    }

    public ByteBuf getMsg() {
        return msg;
    }

    @Override
    public int refCnt() {
        return getMsg().refCnt();
    }

    @Override
    public ReferenceCounted retain() {
        getMsg().retain();
        return this;
    }

    @Override
    public ReferenceCounted retain(final int increment) {
        getMsg().retain(increment);
        return this;
    }

    @Override
    public ReferenceCounted touch() {
        getMsg().touch();
        return this;
    }

    @Override
    public ReferenceCounted touch(final Object hint) {
        getMsg().touch(hint);
        return this;
    }

    @Override
    public boolean release() {
        return getMsg().release();
    }

    @Override
    public boolean release(final int decrement) {
        return getMsg().release(decrement);
    }
}
