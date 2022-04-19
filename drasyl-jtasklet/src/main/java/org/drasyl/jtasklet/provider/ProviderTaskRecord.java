package org.drasyl.jtasklet.provider;

import org.drasyl.identity.DrasylAddress;
import org.drasyl.jtasklet.TaskRecord;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

import static org.drasyl.jtasklet.util.SourceUtil.minifySource;

public class ProviderTaskRecord implements TaskRecord {
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
    private long executionTime;
    private Instant executedTime;

    public ProviderTaskRecord(final DrasylAddress provider,
                              final DrasylAddress broker,
                              final long benchmark,
                              final DrasylAddress consumer,
                              final String token,
                              final String source,
                              final Object[] input) {
        this.provider = provider;
        this.broker = broker;
        this.benchmark = benchmark;
        this.consumer = consumer;
        this.token = token;
        this.source = source;
        this.input = input;
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
                ", source='" + source + '\'' +
                ", input=" + Arrays.toString(input) +
                ", offloadTaskTime=" + offloadTaskTime +
                ", executingTime=" + executingTime +
                ", output=" + Arrays.toString(output) +
                ", executionTime=" + executionTime +
                ", executedTime=" + executedTime +
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

    @Override
    public String[] logTitles() {
        return new String[]{
                "provider",
                "broker",
                "benchmark",
                "consumer",
                "token",
                "source",
                "input",
                "offloadTaskTime",
                "offloadTaskTimeDelta",
                "executingTime",
                "executingTimeDelta",
                "output",
                "executionTime",
                "executedTime",
                "executedTimeDelta"
        };
    }

    @Override
    public Object[] logValues() {
        return new Object[]{
                provider,
                broker,
                benchmark,
                consumer,
                token,
                minifySource(source),
                Arrays.toString(input),
                offloadTaskTime,
                0,
                executingTime != null ? executingTime.toEpochMilli() : 0,
                executingTime != null ? Duration.between(offloadTaskTime, executingTime).toMillis() : -1,
                Arrays.toString(output),
                executionTime,
                executedTime != null ? executedTime.toEpochMilli() : 0,
                executedTime != null ? Duration.between(offloadTaskTime, executedTime).toMillis() : -1
        };
    }
}
