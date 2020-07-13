package org.drasyl.event;

import java.util.Objects;

import static java.util.Objects.requireNonNull;

class AbstractPeerEvent implements PeerEvent {
    protected final Peer peer;

    public AbstractPeerEvent(Peer peer) {
        this.peer = requireNonNull(peer);
    }

    @Override
    public int hashCode() {
        return Objects.hash(peer);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AbstractPeerEvent that = (AbstractPeerEvent) o;
        return Objects.equals(peer, that.peer);
    }

    @Override
    public Peer getPeer() {
        return peer;
    }
}
