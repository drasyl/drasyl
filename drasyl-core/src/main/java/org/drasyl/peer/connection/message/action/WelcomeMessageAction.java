package org.drasyl.peer.connection.message.action;

import org.drasyl.event.Event;
import org.drasyl.event.EventCode;
import org.drasyl.event.Node;
import org.drasyl.peer.connection.message.WelcomeMessage;
import org.drasyl.peer.connection.superpeer.SuperPeerClient;
import org.drasyl.peer.connection.superpeer.SuperPeerConnection;

public class WelcomeMessageAction extends AbstractMessageAction<WelcomeMessage> implements ClientMessageAction<WelcomeMessage> {
    public WelcomeMessageAction(WelcomeMessage message) {
        super(message);
    }

    @Override
    public void onMessageClient(SuperPeerConnection connection,
                                SuperPeerClient superPeerClient) {
        superPeerClient.getOnEvent().accept(new Event(EventCode.EVENT_NODE_ONLINE, Node.of(superPeerClient.getIdentityManager().getIdentity())));
    }
}
