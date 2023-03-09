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
    private final long offloadTaskTime;
    private long executingTime;
    private Object[] output;
    private final List<String> tags;
    private long executionTime;
    private long executedTime;
    private long returnedResult;

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
        this.offloadTaskTime = System.nanoTime();
        this.executingTime = -1;
        this.executedTime = -1;
        this.returnedResult = -1;
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
        executingTime = System.nanoTime();
    }

    public void executed(final Object[] output, final long executionTime) {
        this.output = output;
        this.executionTime = executionTime;
        executedTime = System.nanoTime();
    }

    public void returnedResult() {
        returnedResult = System.nanoTime();
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
                String.join("/", tags),
                minifySource(source),
                Arrays.toString(input),
                Math.floorDiv(offloadTaskTime, 1000),
                0,
                // execute task
                executingTime != -1 ? Math.floorDiv(executingTime, 1000) : -1,
                executingTime != -1 ? Math.floorDiv((executingTime-offloadTaskTime), 1000) : -1,
                output != null ? Arrays.toString(output) : "",
                executionTime,
                executedTime != -1 ? Math.floorDiv(executedTime, 1000) : -1,
                executedTime != -1 ? Math.floorDiv((executedTime-offloadTaskTime), 1000) : -1,
                returnedResult != -1 ? Math.floorDiv(returnedResult, 1000) : -1,
                returnedResult != -1 ? Math.floorDiv((returnedResult-offloadTaskTime), 1000) : -1
        };
    }
}
