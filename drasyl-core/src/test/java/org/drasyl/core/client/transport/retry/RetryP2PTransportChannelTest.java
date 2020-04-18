package org.drasyl.core.client.transport.retry;

import org.drasyl.core.client.transport.OutboundMessageEnvelope;
import org.drasyl.core.client.transport.P2PTransportChannel;
import org.drasyl.core.client.transport.P2PTransportChannelException;
import org.drasyl.core.client.transport.relay.RelayP2PTransportChannel;
import org.drasyl.core.client.transport.relay.RelayP2PTransportChannelProperties;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

public class RetryP2PTransportChannelTest {
    @Mock
    RelayP2PTransportChannel baseChannel1;
    @Mock
    RelayP2PTransportChannel baseChannel2;
    @Mock
    RetryStrategy retryStrategy;
    @Mock
    RelayP2PTransportChannelProperties properties;
    @Mock
    OutboundMessageEnvelope outMsg;
    RetryP2PTransportChannel channel;
    private CompletableFuture<P2PTransportChannel> channelFuture;
    private CompletableFuture<Void> baseChannel1CloseFuture = new CompletableFuture<>();

    private CompletableFuture<P2PTransportChannel> newChannelFuture() {
        if (channelFuture != null && !channelFuture.isDone()) {
            throw new AssertionError("channel future should be done before new one is created!");
        }
        channelFuture = new CompletableFuture<>();
        return channelFuture;
    }

    @Before
    public void before() throws URISyntaxException {
        initMocks(this);

        String systemName = "testSystem";
        when(properties.getSystemName()).thenReturn(systemName);
        String channelName = "testChannel";
        when(properties.getChannel()).thenReturn(channelName);
        when(properties.getJoinTimeout()).thenReturn(Duration.ofSeconds(1));
        when(properties.getRelayUrl()).thenReturn(new URI("ws://localhost:1234"));


        when(retryStrategy.nextChannelAvailable()).thenReturn(true);
        when(retryStrategy.nextChannel(any()))
                .thenAnswer(args -> newChannelFuture());
        when(baseChannel1.start())
                .thenAnswer(args -> CompletableFuture.runAsync(() -> {
                }));

        when(baseChannel1.shutdown())
                .thenAnswer(args -> CompletableFuture.runAsync(() -> {
                }));

        when(baseChannel1.closeFuture())
                .thenReturn(baseChannel1CloseFuture);

        channel = new RetryP2PTransportChannel(
                retryStrategy
        );


        verify(retryStrategy, times(0)).nextChannel(null);
    }


    @Test(timeout = 2000)
    public void startWithoutFailure() {
        CompletableFuture<Void> successStartFuture = channel.start();
        channelFuture.complete(baseChannel1);

        await().atMost(Duration.ofMillis(200)).until(successStartFuture::isDone);
    }


    @Test(timeout = 2000 * 1000)
    public void retryOnStartFailure() {

        CompletableFuture<Void> startFuture = channel.start();

        verify(retryStrategy, timeout(100).times(1)).nextChannel(any());
        channelFuture.completeExceptionally(new P2PTransportChannelException("Test fail start step1!"));

        verify(retryStrategy, timeout(100).times(2)).nextChannel(any());
        channelFuture.completeExceptionally(new P2PTransportChannelException("Test fail start step2!"));

        verify(retryStrategy, timeout(100).times(3)).nextChannel(any());
        channelFuture.completeExceptionally(new P2PTransportChannelException("Test fail start step3!"));

        assertThat(startFuture.isDone(), is(false));


        // stop retrying
        when(retryStrategy.nextChannelAvailable()).thenReturn(false);
        channelFuture.completeExceptionally(new P2PTransportChannelException("Test fail start step!"));

        await().atMost(Duration.ofMillis(500)).until(startFuture::isCompletedExceptionally);

    }

    @Test(timeout = 2000)
    public void sendWithoutFailure() throws P2PTransportChannelException {
        channel.start();
        channelFuture.complete(baseChannel1);

        channel.send(outMsg);
        verify(baseChannel1).send(outMsg);
    }


    @Test(timeout = 2000)
    public void retryOnChannelClose() {

        channel.start();
        channelFuture.complete(baseChannel1);
        verify(retryStrategy, timeout(100).times(1)).nextChannel(any());

        baseChannel1CloseFuture.complete(null);

        // during retry attempts channel.send is not repeated
        verify(retryStrategy, timeout(100).times(2)).nextChannel(any());
        channelFuture.completeExceptionally(new P2PTransportChannelException("Test failure!"));

        verify(retryStrategy, timeout(100).times(3)).nextChannel(any());
        channelFuture.completeExceptionally(new P2PTransportChannelException("Test failure2!"));

        verify(retryStrategy, timeout(100).times(4)).nextChannel(any());
        channelFuture.completeExceptionally(new P2PTransportChannelException("Test failure3!"));

        // stop retrying
        when(retryStrategy.nextChannelAvailable()).thenReturn(false);
        channelFuture.completeExceptionally(new P2PTransportChannelException("Test stop next channel!"));

        verify(retryStrategy, timeout(100).times(5)).nextChannel(any());

        // no more retry
        channelFuture.completeExceptionally(new P2PTransportChannelException("No effect!"));
        verify(retryStrategy, timeout(100).times(5)).nextChannel(any());

        // should throw exception when sending without channel
        assertThrows(P2PTransportChannelException.class, () -> channel.send(outMsg));
    }

    @Test(timeout = 2000)
    public void failShutdown() {

        // shutdown transport channel
        CompletableFuture<Void> sdFut = channel.shutdown();
        // should have nothing to shutdown
        assertThat(sdFut.isDone(), is(true));
    }

    @Test(timeout = 2000)
    public void shutdownChannel() {
        channel.start();
        channelFuture.complete(baseChannel1);

        CompletableFuture<Void> sdFut = channel.shutdown();
        verify(baseChannel1).shutdown();
        await().atMost(Duration.ofMillis(200)).until(sdFut::isDone);
    }


}
