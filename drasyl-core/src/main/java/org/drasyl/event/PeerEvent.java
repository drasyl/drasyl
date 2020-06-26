package org.drasyl.event;

/**
 * Events that refer to a peer.
 */
public interface PeerEvent extends Event {
    Peer getPeer();
}
