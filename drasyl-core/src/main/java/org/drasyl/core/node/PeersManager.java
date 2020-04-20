package org.drasyl.core.node;

import org.drasyl.core.models.Identity;

import java.util.*;

/**
 * This class contains information about other peers. This includes the identities, public keys,
 * available interfaces, connections or relations (e.g. direct/relayed connection, super peer,
 * child, grandchild).
 */
public class PeersManager {
    private final Map<Identity, PeerInformation> peers;
    private final List<Identity> children;
    private Identity superPeer;

    public PeersManager() {
        this(new HashMap<>(), null, new ArrayList<>());
    }

    PeersManager(Map<Identity, PeerInformation> peers,
                 Identity superPeer,
                 List<Identity> children) {
        this.peers = peers;
        this.superPeer = superPeer;
        this.children = children;
    }

    public Map<Identity, PeerInformation> getPeers() {
        return peers;
    }

    public boolean isPeer(Identity identity) {
        return peers.containsKey(identity);
    }

    public PeerInformation addPeer(Identity identity, PeerInformation peer) {
        return peers.put(identity, peer);
    }

    public void addPeers(Map<? extends Identity, ? extends PeerInformation> peers) {
        this.peers.putAll(peers);
    }

    public PeerInformation removePeer(Identity identity) {
        return peers.remove(identity);
    }

    public void removePeers(Identity... identities) {
        for (int i = 0; i < identities.length; i++) {
            peers.remove(identities[i]);
        }
    }

    public List<Identity> getChildren() {
        return children;
    }

    public boolean isChildren(Identity identity) {
        return children.contains(identity);
    }

    public boolean addChildren(Identity... identities) {
        return children.addAll(List.of(identities));
    }

    public boolean removeChildren(Identity... identities) {
        return children.removeAll(List.of(identities));
    }

    public PeerInformation getSuperPeerInformation() {
        return getPeer(getSuperPeer());
    }

    public PeerInformation getPeer(Identity identity) {
        return peers.get(identity);
    }

    public Identity getSuperPeer() {
        return superPeer;
    }

    public boolean isSuperPeer(Identity identity) {
        return Objects.equals(getSuperPeer(), identity);
    }

    public void setSuperPeer(Identity superPeer) {
        this.superPeer = superPeer;
    }

    public void unsetSuperPeer() {
        superPeer = null;
    }
}
