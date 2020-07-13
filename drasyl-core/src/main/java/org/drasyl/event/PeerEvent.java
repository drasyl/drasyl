package org.drasyl.event;

/**
 * Events that refer to a {@link Peer}.
 */
public interface PeerEvent extends Event {
    Peer getPeer();
}
