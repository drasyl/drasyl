package org.drasyl.cli.sdon.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.netty.channel.ChannelPipeline;
import io.netty.util.internal.StringUtil;
import org.drasyl.cli.sdon.handler.policy.ProactiveLatencyMeasurementsPolicyHandler;
import org.drasyl.handler.remote.ApplicationMessageToPayloadCodec;
import org.drasyl.identity.DrasylAddress;

import java.util.Objects;
import java.util.Set;

import static java.util.Objects.requireNonNull;

public class ProactiveLatencyMeasurementsPolicy extends Policy {
    private final Set<DrasylAddress> peers;

    @JsonCreator
    public ProactiveLatencyMeasurementsPolicy(@JsonProperty("peers") final Set<DrasylAddress> peers,
                                              @JsonProperty("currentState") final PolicyState currentState,
                                              @JsonProperty("desiredState") final PolicyState desiredState) {
        super(currentState, desiredState);
        this.peers = requireNonNull(peers);
    }

    public ProactiveLatencyMeasurementsPolicy(final Set<DrasylAddress> peers) {
        super();
        this.peers = requireNonNull(peers);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ProactiveLatencyMeasurementsPolicy that = (ProactiveLatencyMeasurementsPolicy) o;
        return Objects.equals(peers, that.peers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(peers);
    }

    @Override
    public String toString() {
        return "ProactiveLatencyMeasurementsPolicy{" +
                "peers=" + peers +
                ", currentState=" + currentState +
                ", desiredState=" + desiredState +
                '}';
    }

    @JsonGetter("peers")
    public Set<DrasylAddress> peers() {
        return peers;
    }

    public void addPolicy(final ChannelPipeline pipeline) {
        final String handlerName = StringUtil.simpleClassName(this);

        pipeline.addAfter(pipeline.context(ApplicationMessageToPayloadCodec.class).name(), handlerName, new ProactiveLatencyMeasurementsPolicyHandler(this));
    }

    @Override
    public void removePolicy(final ChannelPipeline pipeline) {
        final String handlerName = StringUtil.simpleClassName(this);
        pipeline.remove(handlerName);
    }
}
