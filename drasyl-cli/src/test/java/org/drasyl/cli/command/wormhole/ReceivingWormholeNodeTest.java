package org.drasyl.cli.command.wormhole;

import io.reactivex.rxjava3.disposables.Disposable;
import org.drasyl.DrasylConfig;
import org.drasyl.DrasylNodeComponent;
import org.drasyl.event.MessageEvent;
import org.drasyl.event.NodeNormalTerminationEvent;
import org.drasyl.event.NodeUnrecoverableErrorEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.messenger.Messenger;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.PeerChannelGroup;
import org.drasyl.pipeline.DrasylPipeline;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReceivingWormholeNodeTest {
    private final String password = "123";
    private final CompletableFuture<Void> startSequence = new CompletableFuture<>();
    private final CompletableFuture<Void> shutdownSequence = new CompletableFuture<>();
    private ByteArrayOutputStream outputStream;
    private PrintStream printStream;
    private AtomicBoolean received = new AtomicBoolean();
    @Mock
    private CompressedPublicKey sender;
    @Mock
    private Disposable timeoutGuard;
    @Mock
    private DrasylConfig config;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Identity identity;
    @Mock
    private Messenger messenger;
    @Mock
    private PeersManager peersManager;
    @Mock
    private PeerChannelGroup channelGroup;
    @Mock
    private AtomicBoolean started;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private DrasylPipeline pipeline;
    @Mock
    private Set<URI> endpoints;
    @Mock
    private AtomicBoolean acceptNewConnections;
    @Mock
    private List<DrasylNodeComponent> components;
    @Mock
    private CompletableFuture<Void> doneFuture;
    private ReceivingWormholeNode underTest;

    @BeforeEach
    void setUp() {
        outputStream = new ByteArrayOutputStream();
        printStream = new PrintStream(outputStream, true);
        underTest = new ReceivingWormholeNode(doneFuture, printStream, received, sender, timeoutGuard, config, identity, peersManager, channelGroup, messenger, endpoints, acceptNewConnections, pipeline, components, started, startSequence, shutdownSequence);
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
        void shouldPrintTextOnTextMessage(@Mock(answer = RETURNS_DEEP_STUBS) MessageEvent event) {
            when(event.getPayload()).thenReturn(new TextMessage("Hi"));
            when(event.getSender()).thenReturn(sender);

            underTest.onEvent(event);

            String output = outputStream.toString();
            assertThat(output, containsString("Hi"));
        }

        @Test
        void shouldFailOnWrongPasswordMessage(@Mock(answer = RETURNS_DEEP_STUBS) MessageEvent event) {
            when(event.getPayload()).thenReturn(new WrongPasswordMessage());
            when(event.getSender()).thenReturn(sender);

            underTest.onEvent(event);

            verify(doneFuture).completeExceptionally(any());
        }
    }

    @Nested
    class Shutdown {
        @Test
        void shouldDisposeTimeoutGuard() {
            underTest.shutdown();

            verify(timeoutGuard).dispose();
        }
    }

    @Nested
    class DoneFuture {
        @Test
        void shouldReturnDoneFuture() {
            assertEquals(doneFuture, underTest.doneFuture());
        }
    }

    @Nested
    class RequestText {
        @Test
        void shouldRequestText() {
            underTest.requestText(sender, password);

            verify(pipeline).processOutbound(sender, new PasswordMessage(password));
        }
    }
}