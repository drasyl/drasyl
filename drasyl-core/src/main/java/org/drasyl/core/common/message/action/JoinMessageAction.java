package org.drasyl.core.common.message.action;

import org.drasyl.core.common.message.JoinMessage;
import org.drasyl.core.common.message.WelcomeMessage;
import org.drasyl.core.node.PeerInformation;
import org.drasyl.core.node.connections.ClientConnection;
import org.drasyl.core.node.identity.Identity;
import org.drasyl.core.server.NodeServer;

import static java.util.Objects.requireNonNull;

public class JoinMessageAction extends AbstractMessageAction<JoinMessage> implements ServerMessageAction<JoinMessage> {
    public JoinMessageAction(JoinMessage message) {
        super(message);
    }

    @Override
    public void onMessageServer(ClientConnection session,
                                NodeServer nodeServer) {
        requireNonNull(session);
        requireNonNull(nodeServer);

        Identity peerIdentity = Identity.of(message.getPublicKey());

        // store connection
        nodeServer.getMessenger().getConnectionsManager().addConnection(session);

        // store peer information
        PeerInformation peerInformation = new PeerInformation();
        peerInformation.setPublicKey(message.getPublicKey());
        peerInformation.addEndpoint(message.getEndpoints());
        nodeServer.getPeersManager().addPeer(peerIdentity, peerInformation);
        nodeServer.getPeersManager().addChildren(peerIdentity);

        session.send(new WelcomeMessage(nodeServer.getMyIdentity().getKeyPair().getPublicKey(), nodeServer.getEntryPoints(), message.getId()));
    }
}
