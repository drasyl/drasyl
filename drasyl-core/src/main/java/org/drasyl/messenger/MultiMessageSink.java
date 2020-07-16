package org.drasyl.messenger;

import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.peer.connection.message.RelayableMessage;

import java.util.HashMap;
import java.util.Map;

/**
 * Container for an arbitrary amount of {@link MessageSink}s which can be linked to a public key.
 * <p>
 * Used by {@link org.drasyl.peer.connection.direct.DirectConnectionsManager} to provide one {@link
 * MessageSink} per direct connection to a peer.
 */
public class MultiMessageSink implements MessageSink {
    private final Map<CompressedPublicKey, MessageSink> messageSinks;

    public MultiMessageSink() {
        this(new HashMap<>());
    }

    MultiMessageSink(Map<CompressedPublicKey, MessageSink> messageSinks) {
        this.messageSinks = messageSinks;
    }

    @Override
    public void send(RelayableMessage message) throws MessageSinkException {
        CompressedPublicKey recipient = message.getRecipient();
        MessageSink messageSink = messageSinks.get(recipient);

        if (messageSink != null) {
            messageSink.send(message);
        }
        else {
            throw new NoPathToIdentityException(recipient);
        }
    }

    public void add(CompressedPublicKey publicKey, MessageSink messageSink) {
        messageSinks.put(publicKey, messageSink);
    }

    public void remove(CompressedPublicKey publicKey) {
        messageSinks.remove(publicKey);
    }
}
