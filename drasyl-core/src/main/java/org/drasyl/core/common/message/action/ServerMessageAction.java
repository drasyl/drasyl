package org.drasyl.core.common.message.action;

import org.drasyl.core.common.message.Message;
import org.drasyl.core.node.connections.ClientConnection;
import org.drasyl.core.server.NodeServer;

/**
 * This class describes how a server has to respond when receiving a {@link Message} of
 * type <code>T</code>.
 *
 * @param <T>
 */
public interface ServerMessageAction<T extends Message> extends MessageAction<T> {
    /**
     * Describes how the Server <code>nodeServer</code> should react when a {@link Message} of
     * type
     * <code>T</code> is received from Client in the connection <code>session</code>.
     *
     * @param session
     * @param nodeServer
     */
    void onMessageServer(ClientConnection session, NodeServer nodeServer);
}
