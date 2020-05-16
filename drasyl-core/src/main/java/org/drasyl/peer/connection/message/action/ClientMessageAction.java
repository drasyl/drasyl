package org.drasyl.peer.connection.message.action;

import org.drasyl.peer.connection.message.Message;
import org.drasyl.peer.connection.superpeer.SuperPeerClient;
import org.drasyl.peer.connection.superpeer.SuperPeerConnection;

/**
 * This class describes how a client has to respond when receiving a {@link Message} of type
 * <code>T</code>.
 *
 * @param <T>
 */
public interface ClientMessageAction<T extends Message<?>> extends MessageAction<T> {
    /**
     * Describes how the Client <code>superPeerClient</code> should react when a {@link Message} of
     * type
     * <code>T</code> is received from Server in the connection <code>session</code>.
     *
     * @param connection      the super peer connection
     * @param superPeerClient the super peer client
     */
    void onMessageClient(SuperPeerConnection connection, SuperPeerClient superPeerClient);
}
