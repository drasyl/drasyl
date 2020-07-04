package org.drasyl.peer.connection.direct;

import org.drasyl.DrasylConfig;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.IdentityManager;
import org.drasyl.messenger.Messenger;
import org.drasyl.messenger.MessengerException;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.message.WhoisMessage;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DirectConnectionsManagerTest {
    @Mock
    private DrasylConfig config;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private IdentityManager identityManager;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private PeersManager peersManager;
    @Mock
    private AtomicBoolean opened;
    @Mock
    private Messenger messenger;
    @Mock
    private Set<URI> endpoints;
    @Mock
    private RequestPeerInformationCache requestPeerInformationCache;
    @InjectMocks
    private DirectConnectionsManager underTest;

    @Nested
    class Open {
        @Test
        void shouldSetOpenToTrue() {
            underTest.open();

            assertTrue(opened.get());
        }
    }

    @Nested
    class Close {
        @Test
        void shouldSetOpenToFalse() {
            opened.set(true);

            underTest.close();

            assertFalse(opened.get());
        }
    }

    @Nested
    class CommunicationOccurred {
        @Mock
        private CompressedPublicKey publicKey;

        @Test
        void shouldRequestInformationIfPathsMissingAndCacheEmpty() throws MessengerException {
            underTest = new DirectConnectionsManager(identityManager, peersManager, new AtomicBoolean(true), messenger, Set.of(), requestPeerInformationCache);
            when(peersManager.getPeer(any()).second().isEmpty()).thenReturn(true);
            when(requestPeerInformationCache.add(any())).thenReturn(true);

            underTest.communicationOccurred(publicKey);

            verify(messenger).send(any(WhoisMessage.class));
        }

        @Test
        void shouldNotRequestInformationIfPathsExist() throws MessengerException {
            underTest = new DirectConnectionsManager(identityManager, peersManager, new AtomicBoolean(true), messenger, endpoints, requestPeerInformationCache);
            when(peersManager.getPeer(any()).second().isEmpty()).thenReturn(false);

            underTest.communicationOccurred(publicKey);

            verify(messenger, never()).send(any());
        }

        @Test
        void shouldNotRequestInformationIfPathsMissingButCacheIsNotEmpty() throws MessengerException {
            underTest = new DirectConnectionsManager(identityManager, peersManager, new AtomicBoolean(true), messenger, endpoints, requestPeerInformationCache);
            when(peersManager.getPeer(any()).second().isEmpty()).thenReturn(true);
            when(requestPeerInformationCache.add(any())).thenReturn(false);

            underTest.communicationOccurred(publicKey);

            verify(messenger, never()).send(any());
        }

        @Test
        void shouldNotRequestInformationIfManagerHasNotBeenStarted() throws MessengerException {
            underTest = new DirectConnectionsManager(identityManager, peersManager, new AtomicBoolean(false), messenger, endpoints, requestPeerInformationCache);

            underTest.communicationOccurred(publicKey);

            verify(messenger, never()).send(any());
        }
    }
}