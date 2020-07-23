package org.drasyl.peer.connection.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.reactivex.rxjava3.subjects.Subject;
import org.drasyl.DrasylException;
import org.drasyl.peer.connection.message.QuitMessage;
import org.drasyl.util.DrasylFunction;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static java.time.Duration.ofSeconds;
import static org.drasyl.peer.connection.message.QuitMessage.CloseReason.REASON_SHUTTING_DOWN;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DirectClientTest {
    @Mock
    private EventLoopGroup workerGroup;
    @Mock
    private AtomicInteger nextEndpointPointer;
    @Mock
    private AtomicInteger nextRetryDelayPointer;
    @Mock
    private DrasylFunction<URI, Bootstrap, ClientException> bootstrapSupplier;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Channel channel;
    @Mock
    private Subject<Boolean> connected;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Bootstrap bootstrap;
    @Mock
    private ChannelInitializer<SocketChannel> channelInitializer;
    @Mock
    private DrasylFunction<URI, ChannelInitializer<SocketChannel>, DrasylException> channelInitializerSupplier;
    @Mock
    private Supplier<Set<URI>> endpointsSupplier;
    @Mock
    private BooleanSupplier directConnectionDemand;
    @Mock
    private Runnable onFailure;
    @Mock
    private List<Duration> retryDelays;

    @Nested
    class Open {
        @Test
        void shouldConnectIfClientIsNotAlreadyOpen() throws ClientException {
            when(bootstrapSupplier.apply(any())).thenReturn(bootstrap);
            when(endpointsSupplier.get()).thenReturn(Set.of(URI.create("ws://localhost")));

            try (DirectClient client = new DirectClient(retryDelays, workerGroup, endpointsSupplier, new AtomicBoolean(), () -> false, nextEndpointPointer, nextRetryDelayPointer, bootstrapSupplier, connected, channel, directConnectionDemand, onFailure)) {
                client.open();

                verify(bootstrapSupplier).apply(any());
            }
        }

        @Test
        void shouldNotConnectIfClientIsAlreadyOpen() throws ClientException {
            try (DirectClient client = new DirectClient(retryDelays, workerGroup, endpointsSupplier, new AtomicBoolean(true), () -> true, nextEndpointPointer, nextRetryDelayPointer, bootstrapSupplier, connected, channel, directConnectionDemand, onFailure)) {
                client.open();

                verify(bootstrapSupplier, never()).apply(any());
            }
        }
    }

    @Nested
    class ConditionalScheduledReconnect {
        @Test
        void shouldScheduleReconnectIfClientIsOpenAndSuperPeerRetryDelaysIsNotEmptyAndDemandStillExists() {
            when(directConnectionDemand.getAsBoolean()).thenReturn(true);
            when(retryDelays.get(anyInt())).thenReturn(ofSeconds(1));

            try (DirectClient client = new DirectClient(retryDelays, workerGroup, endpointsSupplier, new AtomicBoolean(true), () -> true, nextEndpointPointer, nextRetryDelayPointer, bootstrapSupplier, connected, channel, directConnectionDemand, onFailure)) {
                client.conditionalScheduledReconnect();

                verify(workerGroup).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
            }
        }

        @Test
        void shouldNotReconnectIfClientIsOpenAndSuperPeerRetryDelaysIsNotEmptyAndNoDemandExists() {
            when(directConnectionDemand.getAsBoolean()).thenReturn(false);

            try (DirectClient client = new DirectClient(retryDelays, workerGroup, endpointsSupplier, new AtomicBoolean(true), () -> true, nextEndpointPointer, nextRetryDelayPointer, bootstrapSupplier, connected, channel, directConnectionDemand, onFailure)) {
                client.conditionalScheduledReconnect();

                verify(workerGroup, never()).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
            }
        }
    }

    @Nested
    class Close {
        @Test
        void shouldSetOpenedToFalseIfClientIsOpen() {
            AtomicBoolean opened = new AtomicBoolean(true);
            DirectClient client = new DirectClient(retryDelays, workerGroup, endpointsSupplier, opened, () -> true, nextEndpointPointer, nextRetryDelayPointer, bootstrapSupplier, connected, channel, directConnectionDemand, onFailure);

            client.close();

            assertFalse(opened.get());
        }

        @Test
        void shouldNotCloseConnectionIfClientIsNotOpen() {
            DirectClient client = new DirectClient(retryDelays, workerGroup, endpointsSupplier, new AtomicBoolean(false), () -> false, nextEndpointPointer, nextRetryDelayPointer, bootstrapSupplier, connected, channel, directConnectionDemand, onFailure);

            client.close();

            verify(channel, never()).writeAndFlush(new QuitMessage(REASON_SHUTTING_DOWN));
            verify(channel, never()).close();
        }
    }
}