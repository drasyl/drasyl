package org.drasyl.jtasklet.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import static java.util.Objects.requireNonNull;
import static org.drasyl.util.Preconditions.requireNonNegative;

public class VmUp implements TaskletMessage {
    private final long executionTime;
    private final String token;

    @JsonCreator
    public VmUp(@JsonProperty("executionTime") final long executionTime,
                @JsonProperty("token") final String token) {
        this.executionTime = requireNonNegative(executionTime);
        this.token = requireNonNull(token);
    }

    @Override
    public String toString() {
        return "VmUp{" +
                "executionTime=" + executionTime +
                ", token=" + token +
                '}';
    }

    public long getExecutionTime() {
        return executionTime;
    }

    public String getToken() {
        return token;
    }
}
