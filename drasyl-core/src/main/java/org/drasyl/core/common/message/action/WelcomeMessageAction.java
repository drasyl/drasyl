package org.drasyl.core.common.message.action;

import org.drasyl.core.client.SuperPeerClient;
import org.drasyl.core.common.message.WelcomeMessage;
import org.drasyl.core.models.Code;
import org.drasyl.core.models.Event;
import org.drasyl.core.models.Node;
import org.drasyl.core.node.connections.SuperPeerConnection;

public class WelcomeMessageAction extends AbstractMessageAction<WelcomeMessage> implements ClientMessageAction<WelcomeMessage> {
    public WelcomeMessageAction(WelcomeMessage message) {
        super(message);
    }

    @Override
    public void onMessageClient(SuperPeerConnection connection,
                                SuperPeerClient superPeerClient) {
        superPeerClient.getOnEvent().accept(new Event(Code.NODE_ONLINE, Node.of(superPeerClient.getIdentityManager().getIdentity())));
    }
}
