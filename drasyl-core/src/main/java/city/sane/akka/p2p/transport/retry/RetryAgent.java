package city.sane.akka.p2p.transport.retry;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class RetryAgent {

    private int attempts;
    private LocalDateTime lastRetryTime = null;


    private final Duration retryDelay;
    private final int maxRetries;
    private final Duration forgetDelay;


    public RetryAgent(Duration retryDelay, int maxRetries, Duration forgetDelay) {
        this.maxRetries = maxRetries;
        this.retryDelay = retryDelay;
        this.forgetDelay = forgetDelay;
        this.attempts = 0;
    }

    public boolean tooManyRetries() {
        LocalDateTime nowTime = LocalDateTime.now();
        if (lastRetryTime != null && nowTime.isAfter(lastRetryTime.plus(forgetDelay))) {
            // forget
            attempts = 0;
        }
        return attempts >= maxRetries;
    }

    public CompletableFuture<Void> retry() {
        LocalDateTime nowTime = LocalDateTime.now();

        attempts++;
        lastRetryTime = nowTime;

        Duration delay = attemptDelayDuration(attempts);
        return CompletableFuture.supplyAsync(
                () -> null,
                CompletableFuture.delayedExecutor(delay.toMillis(), TimeUnit.MILLISECONDS)
        );
    }

    private Duration attemptDelayDuration(long attempt) {
        // first attempt -> 0 delay, second attempt 'retryDelay' delay, ....
        return retryDelay.multipliedBy(attempt - 1);
    }
}

