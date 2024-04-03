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
package org.drasyl.cli.sdo.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.netty.channel.ChannelPipeline;
import io.netty.util.internal.StringUtil;
import org.drasyl.cli.sdo.handler.policy.TunPolicyHandler;
import org.drasyl.cli.util.InetAddressDeserializer;
import org.drasyl.identity.DrasylAddress;

import java.net.InetAddress;
import java.util.Map;
import java.util.Objects;

import static java.util.Objects.requireNonNull;
import static org.drasyl.util.Preconditions.requirePositive;

public class TunPolicy extends Policy {
    public static final String HANDLER_NAME = StringUtil.simpleClassName(TunPolicy.class);
    private final String name;
    private final String subnet;
    private final int mtu;
    private final InetAddress address;
    private final Map<InetAddress, DrasylAddress> routes;
    private final DrasylAddress defaultRoute;

    @JsonCreator
    public TunPolicy(@JsonProperty("name") final String name,
                     @JsonProperty("subnet") final String subnet,
                     @JsonProperty("mtu") final int mtu,
                     @JsonProperty("address") final InetAddress address,
                     @JsonProperty("routes") final Map<InetAddress, DrasylAddress> routes,
                     @JsonProperty("defaultRoute") final DrasylAddress defaultRoute,
                     @JsonProperty("currentState") final PolicyState currentState,
                     @JsonProperty("desiredState") final PolicyState desiredState) {
        super(currentState, desiredState);
        this.name = requireNonNull(name);
        this.subnet = requireNonNull(subnet);
        this.mtu = requirePositive(mtu);
        this.address = requireNonNull(address);
        this.routes = requireNonNull(routes);
        this.defaultRoute = defaultRoute;
    }

    public TunPolicy(final String name,
                     final String subnet,
                     final int mtu,
                     final InetAddress address,
                     final Map<InetAddress, DrasylAddress> routes,
                     final DrasylAddress defaultRoute) {
        super();
        this.name = requireNonNull(name);
        this.subnet = requireNonNull(subnet);
        this.mtu = requirePositive(mtu);
        this.address = requireNonNull(address);
        this.routes = requireNonNull(routes);
        this.defaultRoute = defaultRoute;
    }

    @JsonGetter("name")
    public String name() {
        return name;
    }

    @JsonGetter("subnet")
    public String subnet() {
        return subnet;
    }

    @JsonGetter("mtu")
    public int mtu() {
        return mtu;
    }

    @JsonGetter("address")
    public InetAddress address() {
        return address;
    }

    @JsonDeserialize(keyUsing = InetAddressDeserializer.class)
    @JsonGetter("routes")
    public Map<InetAddress, DrasylAddress> routes() {
        return routes;
    }

    @JsonGetter("defaultRoute")
    public DrasylAddress defaultRoute() {
        return defaultRoute;
    }

    public void addPolicy(final ChannelPipeline pipeline) {
        pipeline.addLast(HANDLER_NAME, new TunPolicyHandler(this));
    }

    @Override
    public void removePolicy(final ChannelPipeline pipeline) {
        pipeline.remove(HANDLER_NAME);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final TunPolicy tunPolicy = (TunPolicy) o;
        return mtu == tunPolicy.mtu && Objects.equals(name, tunPolicy.name) && Objects.equals(subnet, tunPolicy.subnet) && Objects.equals(address, tunPolicy.address) && Objects.equals(routes, tunPolicy.routes) && Objects.equals(defaultRoute, tunPolicy.defaultRoute);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, subnet, mtu, address, routes, defaultRoute);
    }

    @Override
    public String toString() {
        return "TunPolicy{" +
                "name='" + name + '\'' +
                ", subnet='" + subnet + '\'' +
                ", mtu=" + mtu +
                ", address=" + address +
                ", routes=" + routes +
                ", defaultRoute=" + defaultRoute +
                '}';
    }
}
