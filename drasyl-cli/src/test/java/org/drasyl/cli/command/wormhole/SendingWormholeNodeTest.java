package org.drasyl.cli.command.wormhole;

import org.drasyl.DrasylConfig;
import org.drasyl.DrasylNodeComponent;
import org.drasyl.event.MessageEvent;
import org.drasyl.event.NodeNormalTerminationEvent;
import org.drasyl.event.NodeUnrecoverableErrorEvent;
import org.drasyl.identity.Identity;
import org.drasyl.messenger.Messenger;
import org.drasyl.peer.Endpoint;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.PeerChannelGroup;
import org.drasyl.pipeline.DrasylPipeline;
import org.drasyl.plugins.PluginManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SendingWormholeNodeTest {
    private final String password = "123";
    private final AtomicBoolean sent = new AtomicBoolean();
    private final CompletableFuture<Void> startSequence = new CompletableFuture<>();
    private final CompletableFuture<Void> shutdownSequence = new CompletableFuture<>();
    private ByteArrayOutputStream outputStream;
    private PrintStream printStream;
    @Mock
    private CompletableFuture<Void> doneFuture;
    @Mock
    private DrasylConfig config;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Identity identity;
    @Mock
    private Messenger messenger;
    @Mock
    private PeersManager peersManager;
    @Mock
    private AtomicBoolean started;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private DrasylPipeline pipeline;
    @Mock
    private PeerChannelGroup channelGroup;
    @Mock
    private Set<Endpoint> endpoints;
    @Mock
    private AtomicBoolean acceptNewConnections;
    @Mock
    private List<DrasylNodeComponent> components;
    @Mock
    private PluginManager pluginManager;
    private SendingWormholeNode underTest;

    @BeforeEach
    void setUp() {
        outputStream = new ByteArrayOutputStream();
        printStream = new PrintStream(outputStream, true);
        underTest = new SendingWormholeNode(doneFuture, printStream, password, sent, config, identity, peersManager, channelGroup, messenger, endpoints, acceptNewConnections, pipeline, components, pluginManager, started, startSequence, shutdownSequence);
    }

    @Nested
    class OnEvent {
        @Test
        void shouldCompleteExceptionallyOnError(@Mock NodeUnrecoverableErrorEvent event) {
            underTest.onEvent(event);

            verify(doneFuture).completeExceptionally(any());
        }

        @Test
        void shouldCompleteOnTerminationEvent(@Mock NodeNormalTerminationEvent event) {
            underTest.onEvent(event);

            verify(doneFuture).complete(null);
        }

        @Test
        void shouldSendTextOnPasswordMessageWithCorrectPassword(@Mock(answer = RETURNS_DEEP_STUBS) MessageEvent event) {
            when(event.getPayload()).thenReturn(new PasswordMessage("123"));
            underTest.setText("Hi");

            underTest.onEvent(event);

            verify(pipeline).processOutbound(eq(event.getSender()), any(TextMessage.class));
        }

        @Test
        void shouldSendTextOnPasswordMessageWithWrongPassword(@Mock(answer = RETURNS_DEEP_STUBS) MessageEvent event) {
            when(event.getPayload()).thenReturn(new PasswordMessage("456"));
            underTest.setText("Hi");

            underTest.onEvent(event);

            verify(pipeline).processOutbound(eq(event.getSender()), any(WrongPasswordMessage.class));
        }
    }

    @Nested
    class DoneFuture {
        @Test
        void shouldReturnDoneFuture() {
            assertEquals(doneFuture, underTest.doneFuture());
        }
    }
}