package org.drasyl.jtasklet.consumer;

import org.drasyl.identity.DrasylAddress;
import org.drasyl.jtasklet.LoggableRecord;

import java.util.Arrays;
import java.util.List;

import static org.drasyl.jtasklet.util.SourceUtil.minifySource;

public class ConsumerLoggableRecord implements LoggableRecord {
    private final DrasylAddress consumer;
    private final DrasylAddress broker;
    private final String source;
    private final Object[] input;
    private final long resourceRequestTime;
    private long resourceRequestedTime;
    private DrasylAddress provider;
    private String token;
    private List<String> tags;
    private long resourceRespondedTime;
    private long offloadTaskTime;
    private long offloadedTaskTime;
    private Object[] output;
    private long executionTime;
    private long resultReturnedTime;
    private int priority;

    public ConsumerLoggableRecord(final DrasylAddress consumer,
                                  final DrasylAddress broker,
                                  final String source,
                                  final Object[] input) {
        this.consumer = consumer;
        this.broker = broker;
        this.source = source;
        this.input = input;
        this.resourceRequestTime = System.nanoTime();
        this.resourceRequestedTime = -1;
        this.offloadTaskTime = -1;
        this.offloadedTaskTime = -1;
        this.resultReturnedTime = -1;
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
                ", tags='" + String.join(",", tags) + '\'' +
                ", priority='" + priority + '\'' +
                ", resourceRespondedTime=" + resourceRespondedTime +
                ", offloadTaskTime=" + offloadTaskTime +
                ", offloadedTaskTime=" + offloadedTaskTime +
                ", output=" + Arrays.toString(output) +
                ", executionTime=" + executionTime +
                ", resultReturnedTime=" + resultReturnedTime +
                '}';
    }

    public void resourceRequested(final int priority) {
        this.resourceRequestedTime = System.nanoTime();
        this.priority = priority;
    }

    public void resourceResponded(final DrasylAddress provider,
                                  final String token,
                                  final List<String> tags) {
        this.provider = provider;
        this.token = token;
        this.tags = tags;
        resourceRespondedTime = System.nanoTime();
    }

    public void offloadTask() {
        offloadTaskTime = System.nanoTime();
    }

    public void offloadedTask() {
        offloadedTaskTime = System.nanoTime();
    }

    public void resultReturned(final Object[] output, final long executionTime) {
        this.output = output;
        this.executionTime = executionTime;
        resultReturnedTime = System.nanoTime();
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
                "priority",
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
                resourceRequestTime != -1 ? Math.floorDiv(resourceRequestTime, 1000) : -1,
                0,
                resourceRequestedTime != -1 ? Math.floorDiv(resourceRequestedTime, 1000) : -1,
                resourceRequestedTime != -1 ? Math.floorDiv((resourceRequestedTime - resourceRequestTime), 1000) : -1,
                // resource responded
                provider,
                token,
                String.join("/", tags),
                priority,
                resourceRespondedTime != -1 ? Math.floorDiv(resourceRespondedTime, 1000) : -1,
                resourceRespondedTime != -1 ? Math.floorDiv((resourceRespondedTime - resourceRequestTime), 1000) : -1,
                // offload task
                offloadTaskTime != -1 ? Math.floorDiv(offloadTaskTime, 1000) : -1,
                offloadTaskTime != -1 ? Math.floorDiv((offloadTaskTime - resourceRequestTime), 1000) : -1,
                offloadedTaskTime != -1 ? Math.floorDiv(offloadedTaskTime, 1000) : -1,
                offloadedTaskTime != -1 ? Math.floorDiv((offloadedTaskTime - resourceRequestTime), 1000) : -1,
                // return result
                output != null ? Arrays.toString(output) : "",
                executionTime,
                resultReturnedTime != -1 ? Math.floorDiv(resultReturnedTime, 1000) : -1,
                resultReturnedTime != -1 ? Math.floorDiv((resultReturnedTime - resourceRequestTime), 1000) : -1
        };
    }
}
