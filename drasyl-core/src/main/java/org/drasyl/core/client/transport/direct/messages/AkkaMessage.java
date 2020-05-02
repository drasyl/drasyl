package org.drasyl.core.client.transport.direct.messages;

import java.io.Serializable;

public class AkkaMessage implements Serializable {
    private final byte[] blob;
    private final String recipientSystem;
    private final String senderSystem;

    public AkkaMessage(byte[] blob,
                       String senderSystem,
                       String recipientSystem) {
        this.blob = blob;
        this.senderSystem = senderSystem;
        this.recipientSystem = recipientSystem;
    }

    public byte[] getBlob() {
        return blob;
    }

    public String getRecipientSystem() {
        return recipientSystem;
    }

    public String getSenderSystem() {
        return senderSystem;
    }
}
