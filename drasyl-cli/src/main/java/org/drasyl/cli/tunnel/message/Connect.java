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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.netty.channel.Channel;
import io.netty.channel.ChannelId;

import static java.util.Objects.requireNonNull;
import static org.drasyl.util.SecretUtil.maskSecret;

public class Connect implements JacksonCodecTunnelMessage {
    private final String channelId;
    private final String password;

    @JsonCreator
    public Connect(@JsonProperty("channelId") final String channelId,
                   @JsonProperty("password") final String password) {
        this.channelId = requireNonNull(channelId);
        this.password = requireNonNull(password);
    }

    public Connect(final ChannelId channelId, final String password) {
        this(channelId.asLongText(), password);
    }

    public Connect(final Channel channel, final String password) {
        this(channel.id(), password);
    }

    @Override
    public String toString() {
        return "Connect{" +
                "channelId='" + channelId + '\'' +
                ", password='" + maskSecret(password) + '\'' +
                '}';
    }

    @Override
    public String getChannelId() {
        return channelId;
    }

    public String getPassword() {
        return password;
    }
}
