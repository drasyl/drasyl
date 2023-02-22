package org.drasyl.jtasklet.provider;

import org.drasyl.identity.DrasylAddress;
import org.drasyl.jtasklet.LoggableRecord;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.drasyl.jtasklet.util.SourceUtil.minifySource;

public class ProviderLoggableRecord implements LoggableRecord {
    private final DrasylAddress provider;
    private final DrasylAddress broker;
    private final long benchmark;
    private final DrasylAddress consumer;
    private final String token;
    private final String source;
    private final Object[] input;
    private final Instant offloadTaskTime;
    private Instant executingTime;
    private Object[] output;
    private final List<String> tags;
    private long executionTime;
    private Instant executedTime;
    private Instant returnedResult;

    public ProviderLoggableRecord(final DrasylAddress provider,
                                  final DrasylAddress broker,
                                  final long benchmark,
                                  final DrasylAddress consumer,
                                  final String token,
                                  final String source,
                                  final Object[] input,
                                  final List<String> tags) {
        this.provider = provider;
        this.broker = broker;
        this.benchmark = benchmark;
        this.consumer = consumer;
        this.token = token;
        this.source = source;
        this.input = input;
        this.tags = tags;
        offloadTaskTime = Instant.now();
    }

    @Override
    public String toString() {
        return "ProviderTaskRecord{" +
                "provider=" + provider +
                ", broker=" + broker +
                ", benchmark=" + benchmark +
                ", consumer=" + consumer +
                ", token='" + token + '\'' +
                ", tags=" + String.join(",", tags) +
                ", source='" + source + '\'' +
                ", input=" + Arrays.toString(input) +
                ", offloadTaskTime=" + offloadTaskTime +
                ", executingTime=" + executingTime +
                ", output=" + Arrays.toString(output) +
                ", executionTime=" + executionTime +
                ", executedTime=" + executedTime +
                ", returnedResult=" + returnedResult +
                '}';
    }

    public void executing() {
        executingTime = Instant.now();
    }

    public void executed(final Object[] output, final long executionTime) {
        this.output = output;
        this.executionTime = executionTime;
        executedTime = Instant.now();
    }

    public void returnedResult() {
        returnedResult = Instant.now();
    }

    @Override
    public String[] logTitles() {
        return new String[]{
                "provider",
                "broker",
                "benchmark",
                // offload task
                "consumer",
                "token",
                "tags",
                "source",
                "input",
                "offloadTaskTime",
                "offloadTaskTimeDelta",
                // execute task
                "executingTime",
                "executingTimeDelta",
                "output",
                "executionTime",
                "executedTime",
                "executedTimeDelta",
                // return result
                "returnedResult",
                "returnedResultDelta"
        };
    }

    @Override
    public Object[] logValues() {
        return new Object[]{
                provider,
                broker,
                benchmark,
                // offload task
                consumer,
                token,
                String.join(",", tags),
                minifySource(source),
                Arrays.toString(input),
                offloadTaskTime.toEpochMilli(),
                0,
                // execute task
                executingTime != null ? executingTime.toEpochMilli() : -1,
                executingTime != null ? Duration.between(offloadTaskTime, executingTime).toMillis() : -1,
                output != null ? Arrays.toString(output) : "",
                executionTime,
                executedTime != null ? executedTime.toEpochMilli() : -1,
                executedTime != null ? Duration.between(offloadTaskTime, executedTime).toMillis() : -1,
                returnedResult != null ? returnedResult.toEpochMilli() : -1,
                returnedResult != null ? Duration.between(offloadTaskTime, returnedResult).toMillis() : -1
        };
    }
}
