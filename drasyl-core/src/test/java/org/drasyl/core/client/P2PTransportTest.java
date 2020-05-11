package org.drasyl.core.client;

import akka.actor.Address;
import akka.actor.ExtendedActorSystem;
import org.drasyl.core.client.transport.P2PTransport.EventPublisher;
import org.drasyl.core.client.transport.event.P2PTransportErrorEvent;
import org.drasyl.core.client.transport.event.P2PTransportListenEvent;
import org.drasyl.core.client.transport.P2PTransport;
import org.drasyl.core.client.transport.P2PTransportChannel;
import org.drasyl.core.client.transport.P2PTransportException;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class P2PTransportTest {
    private ExtendedActorSystem system;
    private EventPublisher eventPublisher;
    private P2PTransportChannel channel;
    private List<P2PTransportChannel> channels;
    private Address defaultAddress;
    private Set<Address> addresses;
    private P2PActorRefProvider provider;
    private Duration startupTimeout;
    private Duration shutdownTimeout;

    @Before
    public void setUp() {
        system = mock(ExtendedActorSystem.class);
        eventPublisher = mock(EventPublisher.class);
        provider = mock(P2PActorRefProvider.class);
        channel = mock(P2PTransportChannel.class);
        channels = List.of(channel);
        defaultAddress = new Address("bud","my-system");
        addresses = Set.of(defaultAddress);
        startupTimeout = Duration.ofSeconds(1);
        shutdownTimeout = Duration.ofSeconds(1);
    }

    @Test
    public void startSuccessful() {
        when(channel.start()).thenReturn(CompletableFuture.completedFuture(null));

        P2PTransport transport = new P2PTransport(system, provider, eventPublisher, channels, defaultAddress, addresses, startupTimeout, shutdownTimeout);

        transport.start();

        verify(eventPublisher).notifyListeners(any(P2PTransportListenEvent.class));
    }

    @Test(expected = P2PTransportException.class)
    public void startFailed() throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<Void> start = mock(CompletableFuture.class);
        when(start.get(anyLong(), any())).thenThrow(new ExecutionException(null));
        when(channel.start()).thenReturn(start);

        P2PTransport transport = new P2PTransport(system, provider, eventPublisher, channels, defaultAddress, addresses, startupTimeout, shutdownTimeout);

        transport.start();

        verify(eventPublisher).notifyListeners(any(P2PTransportErrorEvent.class));
    }

    @Test(expected = P2PTransportException.class)
    public void startTimeout() throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<Void> start = mock(CompletableFuture.class);
        when(start.get(anyLong(), any())).thenThrow(new TimeoutException());
        when(channel.start()).thenReturn(start);

        P2PTransport transport = new P2PTransport(system, provider, eventPublisher, channels, defaultAddress, addresses, startupTimeout, shutdownTimeout);

        transport.start();

//        verify(eventPublisher).notifyListeners(any(P2PTransportErrorEvent.class));
    }

    @Test
    public void localSystemAddress() {
        P2PTransport transport = new P2PTransport(system, provider, eventPublisher, channels, defaultAddress, addresses, startupTimeout, shutdownTimeout);

        Address defaultAddress = transport.defaultAddress();

        assertEquals(this.defaultAddress, defaultAddress);
    }
}