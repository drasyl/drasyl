package org.drasyl.core.client.transport.retry;

import org.drasyl.core.client.transport.P2PTransportChannel;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class DefaultRetryStrategyTest {

    @Mock
    RetryAgent retryAgent;

    @Mock
    P2PTransportChannel channel;

    @Mock
    P2PTransportChannel channel2;

    @Mock
    Supplier<P2PTransportChannel> supplier;

    private DelayedSwitchRetryStrategy strategy;

    private CompletableFuture<Void> shutdownFuture;
    private CompletableFuture<Void> retryFuture;
    private CompletableFuture<Void> startFuture;

    @Before
    public void before() {
        initMocks(this);
        startFuture = new CompletableFuture<>();
        shutdownFuture = new CompletableFuture<>();
        retryFuture = new CompletableFuture<>();

        when(retryAgent.tooManyRetries()).thenReturn(false);

        when(channel.shutdown()).thenAnswer(args -> shutdownFuture);
        when(retryAgent.retry()).thenAnswer(args -> retryFuture);
        when(channel2.start()).thenAnswer(args -> startFuture);
        when(supplier.get()).thenReturn(channel2);
        strategy = new DelayedSwitchRetryStrategy(retryAgent, supplier);
    }

    @Test
    public void testNextChannel() throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<P2PTransportChannel> channelFuture = strategy.nextChannel(channel);

        verify(channel, times(1)).shutdown();
        verify(retryAgent, times(1)).retry();
        verify(supplier, times(0)).get();
        shutdownFuture.complete(null);

        verify(channel, times(1)).shutdown();
        verify(retryAgent, times(1)).retry();
        verify(supplier, times(0)).get();
        verify(channel2, times(0)).start();
        retryFuture.complete(null);

        verify(channel, times(1)).shutdown();
        verify(retryAgent, times(1)).retry();
        verify(supplier, times(1)).get();
        verify(channel2, times(1)).start();
        startFuture.complete(null);

        P2PTransportChannel c = channelFuture.getNow(null);
        assertThat(c, is(channel2));
    }

    @Test
    public void testNextChannelOnTooManyRetries() {
        when(retryAgent.tooManyRetries()).thenReturn(true);
        assertThat(strategy.nextChannelAvailable(), is(false));

        assertThrows(ExecutionException.class, () -> strategy.nextChannel(channel).get());
    }
}
