package org.drasyl.jtasklet.provider.runtime;

import static java.util.Objects.requireNonNull;
import static org.drasyl.util.Preconditions.requireNonNegative;

public class ExecutionResult {
    private final Object[] output;

    private final long executionTime;

    public ExecutionResult(final Object[] output, final long executionTime) {
        this.output = requireNonNull(output);
        this.executionTime = requireNonNegative(executionTime);
    }

    public Object[] getOutput() {
        return output;
    }

    public long getExecutionTime() {
        return executionTime;
    }
}
