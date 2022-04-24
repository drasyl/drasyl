package org.drasyl.jtasklet.cli;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import org.drasyl.handler.PeersRttHandler.PeerRtt;
import org.drasyl.handler.PeersRttHandler.PeerRtt.Role;

import java.net.InetSocketAddress;

public interface PeerRttMixin {
    @JsonGetter
    Role role();

    @JsonGetter
    InetSocketAddress inetAddress();

    @JsonGetter
    long sent();

    @JsonGetter
    long last();

    @JsonGetter
    double average();

    @JsonGetter
    long best();

    @JsonGetter
    long worst();

    @JsonGetter
    double stDev();

    @JsonCreator
    static PeerRtt of(final Role role,
                      final InetSocketAddress inetAddress,
                      final long rtt) {
        return new PeerRtt(role, inetAddress, rtt);
    }
}
