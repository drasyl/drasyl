package org.drasyl.core.client.transport.retry;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@Ignore
public class RetryAgentTest {

    InetSocketAddress remote;
    InetSocketAddress remote2;


    Duration retryDelay;
    int maxRetries;
    Duration forgetDelay;

    RetryAgent agent;

    @Before
    public void before() {

        remote = new InetSocketAddress("localhost", 1111);
        remote2 = new InetSocketAddress("localhost", 2222);
        retryDelay = Duration.ofMillis(200);
        maxRetries = 3;
        forgetDelay = Duration.ofSeconds(3);

        agent = new RetryAgent(
                retryDelay,
                maxRetries,
                forgetDelay
        );
    }

    @Test
    public void testRetry() throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<Void> future = agent.retry();
        // 0 th attempt without delay
        assertThat(future.get(retryDelay.dividedBy(10).toMillis(), TimeUnit.MILLISECONDS), is(nullValue()));

        final CompletableFuture<Void> future2 = agent.retry();
        try {
            await().atMost(retryDelay.minusMillis(100)).until(future2::isDone);
            fail("should not be done early");
        } catch (Exception ignored) {

        }
        assertThat(future.get(retryDelay.dividedBy(2).toMillis(), TimeUnit.MILLISECONDS), is(nullValue()));

        final CompletableFuture<Void> future3 = agent.retry();
        try {
            await().atMost(retryDelay).until(future3::isDone);
            fail("should not be done early");
        } catch (Exception ignored) {

        }
        assertThat(future3.get(retryDelay.toMillis(), TimeUnit.MILLISECONDS), is(nullValue()));
    }

    @Test
    public void testTooManyRetries() {
        agent.retry();
        assertFalse(agent.tooManyRetries());
        agent.retry();
        assertFalse(agent.tooManyRetries());

        Duration waitDuration = retryDelay.minusMillis(50);
        // should keep state consistent
        try {
            await().atMost(waitDuration)
                    .until(() -> agent.tooManyRetries());
            fail("should not change toomanyretries state!");
        } catch (Exception ignored) {

        }

        agent.retry();
        assertTrue(agent.tooManyRetries());

        // should forget old retries
        await().atMost(forgetDelay.plusMillis(50))
                .until(() -> !agent.tooManyRetries());

        agent.retry();
        assertFalse(agent.tooManyRetries());
        agent.retry();
        assertFalse(agent.tooManyRetries());
        agent.retry();
        assertTrue(agent.tooManyRetries());

    }
}

