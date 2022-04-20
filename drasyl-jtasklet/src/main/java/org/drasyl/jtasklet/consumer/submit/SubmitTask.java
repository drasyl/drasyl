package org.drasyl.jtasklet.consumer.submit;

import java.util.Arrays;

import static java.util.Objects.requireNonNull;

public class SubmitTask {
    private final String task;
    private final Object[] input;

    public SubmitTask(final String task, final Object[] input) {
        this.task = requireNonNull(task);
        this.input = requireNonNull(input);
    }

    @Override
    public String toString() {
        return "SubmitTask{" +
                "task='" + task + '\'' +
                ", input=" + Arrays.toString(input) +
                '}';
    }

    public String source() {
        return task;
    }

    public Object[] input() {
        return input;
    }
}
