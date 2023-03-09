package org.drasyl.jtasklet.broker;

import org.drasyl.identity.DrasylAddress;
import org.drasyl.jtasklet.LoggableRecord;

import java.util.List;

import static java.util.Objects.requireNonNull;

public class BrokerLoggableRecord implements LoggableRecord {
    private final DrasylAddress consumer;
    private final long resourceRequestTime;
    private DrasylAddress provider;
    private long benchmark;
    private String token;
    private List<String> tags;
    private long assignResourceTime;
    private long resourceRespondedTime;
    private int priority;

    public BrokerLoggableRecord(final DrasylAddress consumer) {
        this.consumer = requireNonNull(consumer);
        this.resourceRequestTime = System.nanoTime();
        this.assignResourceTime = -1;
        this.resourceRespondedTime = -1;
    }

    public void assignResource(final DrasylAddress provider,
                               final long benchmark,
                               final String token,
                               final List<String> tags,
                               final int priority) {
        this.provider = provider;
        this.benchmark = benchmark;
        this.token = token;
        this.tags = tags;
        this.priority = priority;
        this.assignResourceTime = System.nanoTime();
    }

    public void resourceResponded() {
        this.resourceRespondedTime = System.nanoTime();
    }

    @Override
    public String[] logTitles() {
        return new String[]{
                "consumer",
                "resourceRequestTime",
                "resourceRequestTimeDelta",
                "provider",
                "benchmark",
                "token",
                "tags",
                "priority",
                "assignResourceTime",
                "assignResourceTimeDelta",
                "resourceRespondedTime",
                "resourceRespondedTimeDelta"
        };
    }

    @Override
    public Object[] logValues() {
        return new Object[]{
                // resource request
                consumer,
                Math.floorDiv(resourceRequestTime, 1000),
                0,
                // assign resource
                provider,
                benchmark,
                token,
                String.join("/", tags),
                priority,
                assignResourceTime != -1 ? Math.floorDiv(assignResourceTime, 1000) : -1,
                assignResourceTime != -1 ? Math.floorDiv((assignResourceTime-resourceRequestTime), 1000) : -1,
                // resource responded
                resourceRespondedTime != -1 ? Math.floorDiv(resourceRespondedTime, 1000) : -1,
                resourceRespondedTime != -1 ? Math.floorDiv((resourceRespondedTime-resourceRequestTime), 1000) : -1,
                };
    }
}
