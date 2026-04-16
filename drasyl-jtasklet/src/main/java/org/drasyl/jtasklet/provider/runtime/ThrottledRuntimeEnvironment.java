package org.drasyl.jtasklet.provider.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

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
        return throttle(result);
    }

    @Override
    public ExecutionResult executeEncrypted(final String key, final String encryptedInput) {
        final ExecutionResult result = throttledEnvironment.executeEncrypted(key, encryptedInput);
        return throttle(result);
    }

    @Override
    public ExecutionResult decrypt(final String ciphertexts, final String functionalKey, final Object weights) {
        final ExecutionResult result = throttledEnvironment.decrypt(ciphertexts, functionalKey, weights);
        return throttle(result);
    }

    @Override
    public ExecutionResult execute(final Path source, final Object... input) throws IOException {
        final ExecutionResult result = throttledEnvironment.execute(source, input);
        return throttle(result);
    }

    @Override
    public ExecutionResult execute(final InputStream source, final Object... input) throws IOException {
        final ExecutionResult result = throttledEnvironment.execute(source, input);
        return throttle(result);
    }

    private ExecutionResult throttle(final ExecutionResult result) {
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
