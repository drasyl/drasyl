package org.drasyl.event;

/**
 * This event signals that the node has established a direct connection to a peer.
 * <p>
 * This is an immutable object.
 */
public class PeerDirectEvent extends AbstractPeerEvent {
    public PeerDirectEvent(final Peer peer) {
        super(peer);
    }

    @Override
    public String toString() {
        return "PeerDirectEvent{" +
                "peer=" + peer +
                '}';
    }
}