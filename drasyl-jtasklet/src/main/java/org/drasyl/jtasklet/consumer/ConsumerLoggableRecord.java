package org.drasyl.jtasklet.consumer;

import org.drasyl.identity.DrasylAddress;
import org.drasyl.jtasklet.LoggableRecord;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

import static org.drasyl.jtasklet.util.SourceUtil.minifySource;

public class ConsumerLoggableRecord implements LoggableRecord {
    private final DrasylAddress consumer;
    private final DrasylAddress broker;
    private final String source;
    private final Object[] input;
    private final Instant resourceRequestTime;
    private Instant resourceRequestedTime;
    private DrasylAddress provider;
    private String token;
    private String[] tags;
    private Instant resourceRespondedTime;
    private Instant offloadTaskTime;
    private Instant offloadedTaskTime;
    private Object[] output;
    private long executionTime;
    private Instant resultReturnedTime;

    public ConsumerLoggableRecord(final DrasylAddress consumer,
                                  final DrasylAddress broker,
                                  final String source,
                                  final Object[] input) {
        this.consumer = consumer;
        this.broker = broker;
        this.source = source;
        this.input = input;
        resourceRequestTime = Instant.now();
    }

    @Override
    public String toString() {
        return "ConsumerTaskRecord{" +
                "consumer=" + broker +
                ", broker=" + broker +
                ", source='" + minifySource(source) + '\'' +
                ", input=" + Arrays.toString(input) +
                ", resourceRequestTime=" + resourceRequestTime +
                ", resourceRequestedTime=" + resourceRequestedTime +
                ", provider=" + provider +
                ", token='" + token + '\'' +
                ", tags='" + Arrays.toString(tags) + '\'' +
                ", resourceRespondedTime=" + resourceRespondedTime +
                ", offloadTaskTime=" + offloadTaskTime +
                ", offloadedTaskTime=" + offloadedTaskTime +
                ", output=" + Arrays.toString(output) +
                ", executionTime=" + executionTime +
                ", resultReturnedTime=" + resultReturnedTime +
                '}';
    }

    public void resourceRequested() {
        resourceRequestedTime = Instant.now();
    }

    public void resourceResponded(final DrasylAddress provider, final String token, final String[] tags) {
        this.provider = provider;
        this.token = token;
        this.tags = tags;
        resourceRespondedTime = Instant.now();
    }

    public void offloadTask() {
        offloadTaskTime = Instant.now();
    }

    public void offloadedTask() {
        offloadedTaskTime = Instant.now();
    }

    public void resultReturned(final Object[] output, final long executionTime) {
        this.output = output;
        this.executionTime = executionTime;
        resultReturnedTime = Instant.now();
    }

    @Override
    public String[] logTitles() {
        return new String[]{
                "consumer",
                "broker",
                "source",
                "input",
                "resourceRequestTime",
                "resourceRequestTimeDelta",
                "resourceRequestedTime",
                "resourceRequestedTimeDelta",
                "provider",
                "token",
                "tags",
                "resourceRespondedTime",
                "resourceRespondedTimeDelta",
                "offloadTaskTime",
                "offloadTaskTimeDelta",
                "offloadedTaskTime",
                "offloadedTaskTimeDelta",
                "output",
                "executionTime",
                "resultReturnedTime",
                "resultReturnedTimeDelta"
        };
    }

    @Override
    public Object[] logValues() {
        return new Object[]{
                consumer,
                broker,
                minifySource(source),
                Arrays.toString(input),
                // resource request
                resourceRequestTime != null ? resourceRequestTime.toEpochMilli() : -1,
                0,
                resourceRequestedTime != null ? resourceRequestedTime.toEpochMilli() : -1,
                resourceRequestedTime != null ? Duration.between(resourceRequestTime, resourceRequestedTime).toMillis() : -1,
                // resource responded
                provider,
                token,
                Arrays.toString(tags),
                resourceRespondedTime != null ? resourceRespondedTime.toEpochMilli() : -1,
                resourceRespondedTime != null ? Duration.between(resourceRequestTime, resourceRespondedTime).toMillis() : -1,
                // offload task
                offloadTaskTime != null ? offloadTaskTime.toEpochMilli() : -1,
                offloadTaskTime != null ? Duration.between(resourceRequestTime, offloadTaskTime).toMillis() : -1,
                offloadedTaskTime != null ? offloadedTaskTime.toEpochMilli() : -1,
                offloadedTaskTime != null ? Duration.between(resourceRequestTime, offloadedTaskTime).toMillis() : -1,
                // return result
                output != null ? Arrays.toString(output) : "",
                executionTime,
                resultReturnedTime != null ? resultReturnedTime.toEpochMilli() : -1,
                resultReturnedTime != null ? Duration.between(resourceRequestTime, resultReturnedTime).toMillis() : -1
        };
    }
}
