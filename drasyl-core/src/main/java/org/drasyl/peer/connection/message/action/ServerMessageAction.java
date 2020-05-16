package org.drasyl.peer.connection.message.action;

import org.drasyl.peer.connection.message.Message;
import org.drasyl.peer.connection.server.NodeServer;
import org.drasyl.peer.connection.server.NodeServerClientConnection;

/**
 * This class describes how a server has to respond when receiving a {@link Message} of type
 * <code>T</code>.
 *
 * @param <T>
 */
public interface ServerMessageAction<T extends Message<?>> extends MessageAction<T> {
    /**
     * Describes how the Server <code>nodeServer</code> should react when a {@link Message} of type
     * <code>T</code> is received from Client in the connection <code>session</code>.
     *
     * @param session
     * @param nodeServer
     */
    void onMessageServer(NodeServerClientConnection session, NodeServer nodeServer);
}
