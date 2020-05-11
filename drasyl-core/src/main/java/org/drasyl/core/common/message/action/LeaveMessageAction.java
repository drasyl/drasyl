package org.drasyl.core.common.message.action;

import org.drasyl.core.common.message.LeaveMessage;
import org.drasyl.core.common.message.ResponseMessage;
import org.drasyl.core.common.message.StatusMessage;
import org.drasyl.core.node.connections.ClientConnection;
import org.drasyl.core.server.NodeServer;

public class LeaveMessageAction extends AbstractMessageAction<LeaveMessage> implements ServerMessageAction<LeaveMessage> {
    public LeaveMessageAction(LeaveMessage message) {
        super(message);
    }

    @Override
    public void onMessageServer(ClientConnection session,
                                NodeServer nodeServer) {
        session.send(new ResponseMessage<>(StatusMessage.OK, message.getId()));
        session.close();
    }
}
