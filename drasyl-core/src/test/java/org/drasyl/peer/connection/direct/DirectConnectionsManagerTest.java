package org.drasyl.peer.connection.direct;

import io.netty.channel.EventLoopGroup;
import org.drasyl.DrasylConfig;
import org.drasyl.event.Event;
import org.drasyl.event.PeerRelayEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.messenger.Messenger;
import org.drasyl.messenger.MessengerException;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.PeerChannelGroup;
import org.drasyl.peer.connection.client.DirectClient;
import org.drasyl.peer.connection.message.WhoisMessage;
import org.drasyl.pipeline.DrasylPipeline;
import org.drasyl.pipeline.HandlerAdapter;
import org.drasyl.pipeline.HandlerContext;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static org.drasyl.peer.connection.direct.DirectConnectionsManager.DIRECT_CONNECTIONS_MANAGER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DirectConnectionsManagerTest {
    @Mock
    private DrasylConfig config;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Identity identity;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private PeersManager peersManager;
    @Mock
    private AtomicBoolean opened;
    @Mock
    private Messenger messenger;
    @Mock
    private Set<URI> endpoints;
    @Mock
    private DirectConnectionDemandsCache directConnectionDemandsCache;
    @Mock
    private RequestPeerInformationCache requestPeerInformationCache;
    @Mock
    private DrasylPipeline pipeline;
    @Mock
    private EventLoopGroup workerGroup;
    @Mock
    private Consumer<Event> eventConsumer;
    @Mock
    private ConcurrentMap<CompressedPublicKey, DirectClient> clients;
    @Mock
    private PeerChannelGroup channelGroup;
    @Mock
    private BooleanSupplier acceptNewConnectionsSupplier;
    private final int maxConnections = 0;
    private DirectConnectionsManager underTest;

    @Nested
    class Open {
        @Test
        void shouldSetOpenToTrue() {
            opened = new AtomicBoolean();
            underTest = new DirectConnectionsManager(config, identity, peersManager, opened, messenger, pipeline, channelGroup, workerGroup, eventConsumer, endpoints, directConnectionDemandsCache, requestPeerInformationCache, clients, acceptNewConnectionsSupplier, maxConnections);
            underTest.open();

            assertTrue(opened.get());
        }

        @Test
        void shouldAddHandlerToPipelineAndListenOnPeerRelayEvents(@Mock(answer = Answers.RETURNS_DEEP_STUBS) PeerRelayEvent event,
                                                                  @Mock CompressedPublicKey publicKey) {
            when(event.getPeer().getPublicKey()).thenReturn(publicKey);
            when(directConnectionDemandsCache.contains(publicKey)).thenReturn(true);
            when(pipeline.addLast(eq(DIRECT_CONNECTIONS_MANAGER), any())).then(invocation -> {
                HandlerAdapter handler = invocation.getArgument(1);
                handler.eventTriggered(mock(HandlerContext.class), event);
                return invocation.getMock();
            });

            underTest = new DirectConnectionsManager(config, identity, peersManager, new AtomicBoolean(), messenger, pipeline, channelGroup, workerGroup, eventConsumer, endpoints, directConnectionDemandsCache, requestPeerInformationCache, clients, acceptNewConnectionsSupplier, maxConnections);
            underTest.open();

            verify(pipeline).addLast(eq(DIRECT_CONNECTIONS_MANAGER), any());
            verify(clients).put(eq(publicKey), any());
        }
    }

    @Nested
    class Close {
        @Test
        void shouldSetOpenToFalse() {
            underTest = new DirectConnectionsManager(config, identity, peersManager, new AtomicBoolean(true), messenger, pipeline, channelGroup, workerGroup, eventConsumer, endpoints, directConnectionDemandsCache, requestPeerInformationCache, clients, acceptNewConnectionsSupplier, maxConnections);

            underTest.close();

            assertFalse(opened.get());
        }

        @Test
        void shouldRemoveHandlerFromPipeline() {
            underTest = new DirectConnectionsManager(config, identity, peersManager, new AtomicBoolean(true), messenger, pipeline, channelGroup, workerGroup, eventConsumer, endpoints, directConnectionDemandsCache, requestPeerInformationCache, clients, acceptNewConnectionsSupplier, maxConnections);

            underTest.close();

            verify(pipeline).remove(DIRECT_CONNECTIONS_MANAGER);
        }

        @Test
        void shouldCloseAndRemoveAllClients(@Mock CompressedPublicKey publicKey,
                                            @Mock DirectClient client) {
            clients = new ConcurrentHashMap(Map.of(publicKey, client));
            underTest = new DirectConnectionsManager(config, identity, peersManager, new AtomicBoolean(true), messenger, pipeline, channelGroup, workerGroup, eventConsumer, endpoints, directConnectionDemandsCache, requestPeerInformationCache, clients, acceptNewConnectionsSupplier, maxConnections);

            underTest.close();

            assertThat(clients, anEmptyMap());
            verify(client).close();
        }
    }

    @Nested
    class CommunicationOccurred {
        @Mock
        private CompressedPublicKey publicKey;

        @Test
        void shouldRequestInformationIfPathsMissingAndCacheEmpty() throws MessengerException {
            underTest = new DirectConnectionsManager(config, identity, peersManager, new AtomicBoolean(true), messenger, pipeline, channelGroup, workerGroup, eventConsumer, Set.of(), directConnectionDemandsCache, requestPeerInformationCache, clients, acceptNewConnectionsSupplier, maxConnections);
            when(peersManager.getPeer(any()).second().isEmpty()).thenReturn(true);
            when(requestPeerInformationCache.add(any())).thenReturn(true);

            underTest.communicationOccurred(publicKey);

            verify(messenger).send(any(WhoisMessage.class));
        }

        @Test
        void shouldNotRequestInformationIfPathsExist() throws MessengerException {
            underTest = new DirectConnectionsManager(config, identity, peersManager, new AtomicBoolean(true), messenger, pipeline, channelGroup, workerGroup, eventConsumer, endpoints, directConnectionDemandsCache, requestPeerInformationCache, clients, acceptNewConnectionsSupplier, maxConnections);
            when(peersManager.getPeer(any()).second().isEmpty()).thenReturn(false);

            underTest.communicationOccurred(publicKey);

            verify(messenger, never()).send(any());
        }

        @Test
        void shouldNotRequestInformationIfPathsMissingButCacheIsNotEmpty() throws MessengerException {
            underTest = new DirectConnectionsManager(config, identity, peersManager, new AtomicBoolean(true), messenger, pipeline, channelGroup, workerGroup, eventConsumer, endpoints, directConnectionDemandsCache, requestPeerInformationCache, clients, acceptNewConnectionsSupplier, maxConnections);
            when(peersManager.getPeer(any()).second().isEmpty()).thenReturn(true);
            when(requestPeerInformationCache.add(any())).thenReturn(false);

            underTest.communicationOccurred(publicKey);

            verify(messenger, never()).send(any());
        }

        @Test
        void shouldNotRequestInformationIfManagerHasNotBeenStarted() throws MessengerException {
            underTest = new DirectConnectionsManager(config, identity, peersManager, new AtomicBoolean(false), messenger, pipeline, channelGroup, workerGroup, eventConsumer, endpoints, directConnectionDemandsCache, requestPeerInformationCache, clients, acceptNewConnectionsSupplier, maxConnections);

            underTest.communicationOccurred(publicKey);

            verify(messenger, never()).send(any());
        }

        @Test
        void shouldInitiateDirectConnectionIfPathMissingAndEndpointsGiven() {
            underTest = new DirectConnectionsManager(config, identity, peersManager, new AtomicBoolean(true), messenger, pipeline, channelGroup, workerGroup, eventConsumer, endpoints, directConnectionDemandsCache, requestPeerInformationCache, clients, acceptNewConnectionsSupplier, maxConnections);
            when(peersManager.getPeer(any()).second().isEmpty()).thenReturn(false);
            when(directConnectionDemandsCache.contains(any())).thenReturn(true);

            underTest.communicationOccurred(publicKey);

            verify(clients).put(eq(publicKey), any());
        }
    }
}