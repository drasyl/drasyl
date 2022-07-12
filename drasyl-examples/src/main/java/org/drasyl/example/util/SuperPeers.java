package org.drasyl.example.util;

import org.drasyl.identity.IdentityPublicKey;

import java.net.InetSocketAddress;
import java.util.Map;

public final class SuperPeers {
    public static final Map<IdentityPublicKey, InetSocketAddress> SUPER_PEERS = Map.of(
            IdentityPublicKey.of("c0900bcfabc493d062ecd293265f571edb70b85313ba4cdda96c9f77163ba62d"), new InetSocketAddress("sp-fra1.drasyl.org", 22527),
            IdentityPublicKey.of("5b4578909bf0ad3565bb5faf843a9f68b325dd87451f6cb747e49d82f6ce5f4c"), new InetSocketAddress("sp-nbg2.drasyl.org", 22527)
    );

    private SuperPeers() {
        // util class
    }
}
