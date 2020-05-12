package org.drasyl.core.common.message.action;

import org.drasyl.core.client.SuperPeerClient;
import org.drasyl.core.common.message.Message;

/**
 * This class describes how a client has to respond when receiving a {@link ResponseMessage} for Message of
 * type <code>T</code>.
 *
 * @param <T>
 */
public interface RespondableClientMessageAction<T extends Message> extends ClientMessageAction<T> {
    /**
     * Describes how the Client <code>superPeerClient</code> should react when a response for a
     * {@link Message} of type <code>T</code> is received from Server in the connection
     * <code>session</code>.
     *
     * @param responseMsgID
     * @param session
     * @param superPeerClient
     */
    // FIXME: replace "Object" at parameter "session" with actual class
    void onResponseClient(String responseMsgID,
                          Object session,
                          SuperPeerClient superPeerClient);
}
