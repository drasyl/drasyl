package org.drasyl.peer.connection.handler;

import io.netty.channel.embedded.EmbeddedChannel;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.Consumer;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PeerCommunicationWatcherTest {
    @Mock
    private CompressedPublicKey ownPublicKey;
    @Mock
    private Consumer<CompressedPublicKey> peerCommunicationConsumer;

    @Test
    void shouldCallPeerCommunicationConsumerForEveryOutgoingApplicationMessageSentFromMe(@Mock(answer = Answers.RETURNS_DEEP_STUBS) ApplicationMessage applicationMessage) {
        when(applicationMessage.getSender()).thenReturn(ownPublicKey);

        EmbeddedChannel channel = new EmbeddedChannel(new PeerCommunicationWatcher(ownPublicKey, peerCommunicationConsumer));
        channel.writeOutbound(applicationMessage);

        verify(peerCommunicationConsumer).accept(applicationMessage.getRecipient());
    }

    @Test
    void shouldNotCallPeerCommunicationConsumerForRelayedOutgoingApplicationMessages(@Mock(answer = Answers.RETURNS_DEEP_STUBS) ApplicationMessage applicationMessage) {
        EmbeddedChannel channel = new EmbeddedChannel(new PeerCommunicationWatcher(ownPublicKey, peerCommunicationConsumer));
        channel.writeOutbound(applicationMessage);

        verify(peerCommunicationConsumer, never()).accept(any());
    }
}