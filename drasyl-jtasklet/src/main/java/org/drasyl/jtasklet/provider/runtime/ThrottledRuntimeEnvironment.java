package org.drasyl.jtasklet.provider.runtime;

import static java.util.Objects.requireNonNull;
import static org.drasyl.util.Preconditions.requireInRange;

public class ThrottledRuntimeEnvironment extends AbstractRuntimeEnvironment {
    private final RuntimeEnvironment throttledEnvironment;
    private final float throttleRate;

    public ThrottledRuntimeEnvironment(final RuntimeEnvironment throttledEnvironment,
                                       final float throttleRate) {
        this.throttledEnvironment = requireNonNull(throttledEnvironment);
        this.throttleRate = requireInRange(throttleRate, 1, Integer.MAX_VALUE);
    }

    @Override
    public ExecutionResult execute(final CharSequence source, final Object... input) {
        final ExecutionResult result = throttledEnvironment.execute(source, input);
        final long unthrottledTime = result.getExecutionTime();
        final long throttledTime = (long) (result.getExecutionTime() * throttleRate);

        if (throttledTime > unthrottledTime) {
            final long delayTime = throttledTime - unthrottledTime;

            try {
                Thread.sleep(delayTime);
            }
            catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        return new ExecutionResult(result.getOutput(), throttledTime);
    }
}
