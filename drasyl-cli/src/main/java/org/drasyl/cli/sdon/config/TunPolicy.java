/*
 * Copyright (c) 2020-2024 Heiko Bornholdt and Kevin Röbert
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

package org.drasyl.cli.sdon.config;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.netty.channel.ChannelPipeline;
import io.netty.util.AttributeKey;
import io.netty.util.internal.StringUtil;
import org.drasyl.channel.tun.TunChannel;
import org.drasyl.cli.sdon.handler.policy.TunPolicyHandler;
import org.drasyl.cli.util.InetAddressDeserializer;
import org.drasyl.cli.util.LuaHelper;
import org.drasyl.identity.DrasylAddress;
import org.luaj.vm2.LuaNumber;
import org.luaj.vm2.LuaString;
import org.luaj.vm2.LuaValue;

import java.net.InetAddress;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static org.drasyl.util.Preconditions.requirePositive;

/**
 * Policy for the TUN device.
 */
public class TunPolicy extends Policy {
    public static final String HANDLER_NAME = StringUtil.simpleClassName(TunPolicy.class);
    public static final AttributeKey<TunChannel> TUN_CHANNEL_KEY = AttributeKey.valueOf("TUN_CHANNEL_KEY");
    private final InetAddress address;
    private final short netmask;
    private final Map<InetAddress, DrasylAddress> mapping;

    public TunPolicy(@JsonProperty("address") final InetAddress address,
                     @JsonProperty("netmask") final short netmask,
                     @JsonProperty("mapping") final Map<InetAddress, DrasylAddress> mapping) {
        this.address = requireNonNull(address);
        this.netmask = requirePositive(netmask);
        this.mapping = requireNonNull(mapping);
    }

    @JsonGetter("address")
    public InetAddress address() {
        return address;
    }

    @JsonGetter("netmask")
    public short netmask() {
        return netmask;
    }

    @JsonDeserialize(keyUsing = InetAddressDeserializer.class)
    @JsonGetter("mapping")
    public Map<InetAddress, DrasylAddress> mapping() {
        return mapping;
    }

    public void addPolicy(final ChannelPipeline pipeline) {
        pipeline.addLast(HANDLER_NAME, new TunPolicyHandler(this));
    }

    @Override
    public void removePolicy(final ChannelPipeline pipeline) {
        pipeline.remove(HANDLER_NAME);
    }

    @Override
    public LuaValue luaValue() {
        final LuaValue table = super.luaValue();
        table.set("address", LuaString.valueOf(address().getHostAddress()));
        table.set("netmask", LuaNumber.valueOf(netmask));
        final Map<String, Object> stringMapping = mapping.entrySet().stream().collect(Collectors.toMap(e -> e.getKey().getHostAddress(), e -> e.getValue().toString()));
        table.set("mapping", LuaHelper.createTable(stringMapping));
        return table;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final TunPolicy tunPolicy = (TunPolicy) o;
        return netmask == tunPolicy.netmask && Objects.equals(address, tunPolicy.address) && Objects.equals(mapping, tunPolicy.mapping);
    }

    @Override
    public int hashCode() {
        return Objects.hash(address, netmask, mapping);
    }

    @Override
    public String toString() {
        return "TunPolicy{" +
                "address=" + address +
                ", netmask=" + netmask +
                ", mapping=" + mapping +
                ", state=" + state +
                '}';
    }
}
