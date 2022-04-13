package org.drasyl.jtasklet.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import static org.drasyl.util.Preconditions.requireNonNegative;

public class VmUp implements TaskletMessage {
    private final long executionTime;

    @JsonCreator
    public VmUp(@JsonProperty("executionTime") final long executionTime) {
        this.executionTime = requireNonNegative(executionTime);
    }

    @Override
    public String toString() {
        return "VmUp{" +
                "executionTime=" + executionTime +
                '}';
    }

    public long getExecutionTime() {
        return executionTime;
    }
}
