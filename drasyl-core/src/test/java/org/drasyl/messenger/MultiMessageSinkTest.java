package org.drasyl.messenger;

import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.peer.connection.message.RelayableMessage;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MultiMessageSinkTest {
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Map<CompressedPublicKey, MessageSink> messageSinks;

    @Nested
    class Send {
        @Mock
        private RelayableMessage message;

        @Test
        void shouldCallCorrectSink(@Mock CompressedPublicKey recipient,
                                   @Mock MessageSink messageSink) throws MessageSinkException {
            when(message.getRecipient()).thenReturn(recipient);
            when(messageSinks.get(recipient)).thenReturn(messageSink);

            MultiMessageSink underTest = new MultiMessageSink(messageSinks);
            underTest.send(message);

            verify(messageSink).send(message);
        }
    }

    @Nested
    class Add {
        @Mock
        private CompressedPublicKey publicKey;
        @Mock
        private MessageSink messageSink;

        @Test
        void shouldAddSink() {
            MultiMessageSink underTest = new MultiMessageSink(messageSinks);
            underTest.add(publicKey, messageSink);

            verify(messageSinks).put(publicKey, messageSink);
        }
    }

    @Nested
    class Remove {
        @Mock
        private CompressedPublicKey publicKey;

        @Test
        void shouldRemoveSink() {
            MultiMessageSink underTest = new MultiMessageSink(messageSinks);
            underTest.remove(publicKey);

            verify(messageSinks).remove(publicKey);
        }
    }
}