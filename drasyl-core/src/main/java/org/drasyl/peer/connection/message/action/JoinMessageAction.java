package org.drasyl.peer.connection.message.action;

import org.drasyl.identity.Identity;
import org.drasyl.peer.PeerInformation;
import org.drasyl.peer.connection.message.JoinMessage;
import org.drasyl.peer.connection.message.WelcomeMessage;
import org.drasyl.peer.connection.server.NodeServer;
import org.drasyl.peer.connection.server.NodeServerClientConnection;

import static java.util.Objects.requireNonNull;

public class JoinMessageAction extends AbstractMessageAction<JoinMessage> implements ServerMessageAction<JoinMessage> {
    public JoinMessageAction(JoinMessage message) {
        super(message);
    }

    @Override
    public void onMessageServer(NodeServerClientConnection session,
                                NodeServer nodeServer) {
        requireNonNull(session);
        requireNonNull(nodeServer);

        Identity peerIdentity = Identity.of(message.getPublicKey());

        // store peer information
        PeerInformation peerInformation = new PeerInformation();
        peerInformation.setPublicKey(message.getPublicKey());
        peerInformation.addEndpoint(message.getEndpoints());
        nodeServer.getPeersManager().addPeer(peerIdentity, peerInformation);
        nodeServer.getPeersManager().addChildren(peerIdentity);

        session.send(new WelcomeMessage(nodeServer.getMyIdentity().getKeyPair().getPublicKey(), nodeServer.getEntryPoints(), message.getId()));
    }
}
