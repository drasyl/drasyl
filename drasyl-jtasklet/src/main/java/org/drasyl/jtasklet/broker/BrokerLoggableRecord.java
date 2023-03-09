package org.drasyl.jtasklet.broker;

import org.drasyl.identity.DrasylAddress;
import org.drasyl.jtasklet.LoggableRecord;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static java.util.Objects.requireNonNull;

public class BrokerLoggableRecord implements LoggableRecord {
    private final DrasylAddress consumer;
    private final Instant resourceRequestTime;
    private DrasylAddress provider;
    private long benchmark;
    private String token;
    private List<String> tags;
    private Instant assignResourceTime;
    private Instant resourceRespondedTime;
    private int priority;

    public BrokerLoggableRecord(final DrasylAddress consumer) {
        this.consumer = requireNonNull(consumer);
        this.resourceRequestTime = Instant.now();
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
        this.assignResourceTime = Instant.now();
    }

    public void resourceResponded() {
        this.resourceRespondedTime = Instant.now();
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
                resourceRequestTime.toEpochMilli(),
                0,
                // assign resource
                provider,
                benchmark,
                token,
                String.join("/", tags),
                priority,
                assignResourceTime != null ? assignResourceTime.toEpochMilli() : -1,
                assignResourceTime != null ? Duration.between(resourceRequestTime, assignResourceTime).toMillis() : -1,
                // resource responded
                resourceRespondedTime != null ? resourceRespondedTime.toEpochMilli() : -1,
                resourceRespondedTime != null ? Duration.between(resourceRequestTime, resourceRespondedTime).toMillis() : -1,
                };
    }
}
