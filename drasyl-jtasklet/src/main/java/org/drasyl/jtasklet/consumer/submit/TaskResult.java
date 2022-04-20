package org.drasyl.jtasklet.consumer.submit;

import static java.util.Objects.requireNonNull;

public class TaskResult {
    private final Object[] output;

    public TaskResult(final Object[] output) {
        this.output = requireNonNull(output);
    }

    public Object[] output() {
        return output;
    }
}
