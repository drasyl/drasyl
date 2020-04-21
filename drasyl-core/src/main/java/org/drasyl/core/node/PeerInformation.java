package org.drasyl.core.node;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Contains information on a specific peer (e.g. known endpoints and active connections).
 */
public class PeerInformation {
    private final List<URI> endpoints;
    private final List<PeerConnection> connections;

    public PeerInformation() {
        this(new ArrayList<>(), new ArrayList<>());
    }

    PeerInformation(List<URI> endpoints,
                    List<PeerConnection> connections) {
        this.endpoints = endpoints;
        this.connections = connections;
    }

    public List<PeerConnection> getConnections() {
        return connections;
    }

    public boolean addPeerConnection(PeerConnection... connections) {
        return this.connections.addAll(List.of(connections));
    }

    public boolean removePeerConnection(PeerConnection... connections) {
        return this.connections.removeAll(List.of(connections));
    }

    public List<URI> getEndpoints() {
        return endpoints;
    }

    public boolean addEndpoint(URI... endpoints) {
        return this.endpoints.addAll(List.of(endpoints));
    }

    public boolean removeEndpoint(URI... endpoints) {
        return this.endpoints.removeAll(List.of(endpoints));
    }
}
