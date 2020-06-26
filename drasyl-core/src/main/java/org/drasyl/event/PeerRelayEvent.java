package org.drasyl.event;

/**
 * This event signals that the node has established a connection via a relay to a peer.
 */
public class PeerRelayEvent extends AbstractPeerEvent {
    public PeerRelayEvent(Peer peer) {
        super(peer);
    }

    @Override
    public String toString() {
        return "PeerRelayEvent{" +
                "peer=" + peer +
                '}';
    }
}
