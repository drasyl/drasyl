package org.drasyl.event;

/**
 * This event signals that the node has established a connection via a super peer to a peer.
 * <p>
 * This is an immutable object.
 */
public class PeerRelayEvent extends AbstractPeerEvent {
    public PeerRelayEvent(final Peer peer) {
        super(peer);
    }

    @Override
    public String toString() {
        return "PeerRelayEvent{" +
                "peer=" + peer +
                '}';
    }
}