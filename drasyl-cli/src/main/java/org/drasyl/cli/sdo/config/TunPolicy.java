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

    @JsonCreator
    public TunPolicy(@JsonProperty("name") final String name,
                     @JsonProperty("subnet") final String subnet,
                     @JsonProperty("mtu") final int mtu,
                     @JsonProperty("address") final InetAddress address,
                     @JsonProperty("routes") final Map<InetAddress, DrasylAddress> routes) {
        this.name = requireNonNull(name);
        this.subnet = requireNonNull(subnet);
        this.mtu = requirePositive(mtu);
        this.address = requireNonNull(address);
        this.routes = requireNonNull(routes);
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

    public void addPolicy(final ChannelPipeline pipeline) {
        final String handlerName = StringUtil.simpleClassName(this);

        pipeline.addLast(handlerName, new TunPolicyHandler(this));
    }

    @Override
    public void removePolicy(final ChannelPipeline pipeline) {
        pipeline.remove(HANDLER_NAME);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TunPolicy tunPolicy = (TunPolicy) o;
        return mtu == tunPolicy.mtu && Objects.equals(name, tunPolicy.name) && Objects.equals(subnet, tunPolicy.subnet) && Objects.equals(address, tunPolicy.address) && Objects.equals(routes, tunPolicy.routes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, subnet, mtu, address, routes);
    }

    @Override
    public String toString() {
        return "TunPolicy{" +
                "name='" + name + '\'' +
                ", subnet='" + subnet + '\'' +
                ", mtu=" + mtu +
                ", address=" + address +
                ", routes=" + routes +
                '}';
    }
}
