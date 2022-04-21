package org.drasyl.jtasklet.broker;

import org.drasyl.identity.DrasylAddress;
import org.drasyl.jtasklet.LoggableRecord;
import org.drasyl.jtasklet.broker.ResourceProvider.ProviderState;

import static java.util.Objects.requireNonNull;

public class BrokerLoggableRecord implements LoggableRecord {
    private final DrasylAddress provider;
    private final long benchmark;
    private int succeededTasks;
    private int failedTasks;
    private float errorRate;
    private ProviderState state;
    private long timeSinceLastStateChange;
    private DrasylAddress assignedTo;

    public BrokerLoggableRecord(final DrasylAddress provider, final long benchmark) {
        this.provider = requireNonNull(provider);
        this.benchmark = benchmark;
    }

    @Override
    public String toString() {
        return "BrokerLoggableRecord{" +
                "provider=" + provider +
                ", benchmark=" + benchmark +
                ", succeededTasks=" + succeededTasks +
                ", failedTasks=" + failedTasks +
                ", errorRate=" + errorRate +
                ", state=" + state +
                ", timeSinceLastStateChange=" + timeSinceLastStateChange +
                ", assignedTo=" + assignedTo +
                '}';
    }

    public void unregister(final int succeededTasks,
                           final int failedTasks,
                           final float errorRate,
                           final ProviderState state,
                           final long timeSinceLastStateChange,
                           final DrasylAddress assignedTo) {
        this.succeededTasks = succeededTasks;
        this.failedTasks = failedTasks;
        this.errorRate = errorRate;
        this.state = state;
        this.timeSinceLastStateChange = timeSinceLastStateChange;
        this.assignedTo = assignedTo;
    }

    @Override
    public String[] logTitles() {
        return new String[] {
                "provider",
                "benchmark",
                "succeededTasks",
                "failedTasks",
                "errorRate",
                "state",
                "timeSinceLastStateChange",
                "assignedTo"
        };
    }

    @Override
    public Object[] logValues() {
        return new Object[] {
            provider,
            benchmark,
            succeededTasks,
            failedTasks,
            errorRate,
            state,
            timeSinceLastStateChange,
            assignedTo
        };
    }
}
