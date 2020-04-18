package org.drasyl.core.models;

import java.util.Objects;

public class Peer {
    private final Identity address;

    public Peer(Identity address) {
        this.address = address;
    }

    public Identity getAddress() {
        return address;
    }

    @Override
    public int hashCode() {
        return Objects.hash(address);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Peer peer = (Peer) o;
        return Objects.equals(address, peer.address);
    }

    @Override
    public String toString() {
        return "Peer{" +
                "address=" + address +
                '}';
    }
}
