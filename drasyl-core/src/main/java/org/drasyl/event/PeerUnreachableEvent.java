package org.drasyl.event;

/**
 * This event signals that the node is unable to reach given peer.
 */
public class PeerUnreachableEvent extends AbstractPeerEvent {
    public PeerUnreachableEvent(Peer peer) {
        super(peer);
    }

    @Override
    public String toString() {
        return "PeerUnreachableEvent{" +
                "peer=" + peer +
                '}';
    }
}
