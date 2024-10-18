package org.drasyl.cli.sdon.config;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.netty.channel.ChannelPipeline;
import io.netty.util.internal.StringUtil;
import org.drasyl.cli.sdon.handler.policy.IpPolicyHandler;

import java.net.InetAddress;
import java.util.Objects;

import static java.util.Objects.requireNonNull;
import static org.drasyl.util.Preconditions.requirePositive;

public class IpPolicy extends Policy {
    public static final String HANDLER_NAME = StringUtil.simpleClassName(IpPolicy.class);
    private final InetAddress address;
    private final short netmask;

    public IpPolicy(@JsonProperty("address") final InetAddress address,
                    @JsonProperty("netmask") final short netmask) {
        this.address = requireNonNull(address);
        this.netmask = requirePositive(netmask);
    }

    @JsonGetter("address")
    public InetAddress address() {
        return address;
    }

    @JsonGetter("netmask")
    public short netmask() {
        return netmask;
    }

    public void addPolicy(final ChannelPipeline pipeline) {
        if (false) {
            pipeline.addLast(HANDLER_NAME, new IpPolicyHandler(this));
        }
    }

    @Override
    public void removePolicy(final ChannelPipeline pipeline) {
        pipeline.remove(HANDLER_NAME);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final IpPolicy ipPolicy = (IpPolicy) o;
        return netmask == ipPolicy.netmask && Objects.equals(address, ipPolicy.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(address, netmask);
    }

    @Override
    public String toString() {
        return "IpPolicy{" +
                "address=" + address +
                ", netmask=" + netmask +
                '}';
    }
}
