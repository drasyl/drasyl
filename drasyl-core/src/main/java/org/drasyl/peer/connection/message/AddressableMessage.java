package org.drasyl.peer.connection.message;

import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.ProofOfWork;

public interface AddressableMessage extends Message {
    CompressedPublicKey getRecipient();

    CompressedPublicKey getSender();

    ProofOfWork getProofOfWork();
}
