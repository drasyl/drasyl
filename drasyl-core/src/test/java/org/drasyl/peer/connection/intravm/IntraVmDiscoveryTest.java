package org.drasyl.peer.connection.intravm;

import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.messenger.Messenger;
import org.drasyl.peer.Path;
import org.drasyl.peer.PeerInformation;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.message.RelayableMessage;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.drasyl.peer.connection.intravm.IntraVmDiscovery.MESSAGE_SINK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntraVmDiscoveryTest {
    @Mock
    private CompressedPublicKey publicKey;
    @Mock
    private Messenger messenger;
    @Mock
    private PeersManager peersManager;
    @Mock
    private Path path;
    @Mock
    private PeerInformation peerInformation;

    @Nested
    class Open {
        @Test
        void shouldSetMessageSink() {
            try (final IntraVmDiscovery underTest = new IntraVmDiscovery(publicKey, messenger, peersManager, path, peerInformation, new AtomicBoolean(false))) {
                underTest.open();

                verify(messenger).setIntraVmSink(any());
            }
        }

        @Test
        void shouldAddToDiscoveries() {
            try (final IntraVmDiscovery underTest = new IntraVmDiscovery(publicKey, messenger, peersManager, path, peerInformation, new AtomicBoolean(false))) {
                underTest.open();

                assertThat(IntraVmDiscovery.discoveries, aMapWithSize(1));
            }
        }
    }

    @Nested
    class Close {
        @Test
        void shouldRemoveMessageSink() {
            try (final IntraVmDiscovery underTest = new IntraVmDiscovery(publicKey, messenger, peersManager, path, peerInformation, new AtomicBoolean(true))) {
                underTest.close();

                verify(messenger).unsetIntraVmSink();
            }
        }

        @Test
        void shouldRemovePeerInformation() {
            try (final IntraVmDiscovery underTest = new IntraVmDiscovery(publicKey, messenger, peersManager, path, peerInformation, new AtomicBoolean(true))) {
                underTest.close();

                assertThat(IntraVmDiscovery.discoveries, aMapWithSize(0));
            }
        }
    }

    @Nested
    class MessageSink {
        @Test
        void shouldThrowExceptionForUnknownRecipient(@Mock final RelayableMessage message) {
            assertThrows(ExecutionException.class, () -> MESSAGE_SINK.send(message).get());
        }

        @Test
        void shouldPassMessageToPathForKnownRecipient(@Mock final RelayableMessage message) {
            when(message.getRecipient()).thenReturn(publicKey);

            try (final IntraVmDiscovery underTest = new IntraVmDiscovery(publicKey, messenger, peersManager, path, peerInformation, new AtomicBoolean(false))) {
                underTest.open();
                MESSAGE_SINK.send(message);

                verify(path).send(message);
            }
        }
    }
}