package org.drasyl.jtasklet.cli;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.drasyl.handler.PeersRttHandler.PeerRtt;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.IdentityPublicKey;

import java.util.Map;

public interface PeersRttReportMixin {
    @JsonGetter
    long time();

    @JsonGetter
    @JsonDeserialize(keyAs = IdentityPublicKey.class)
    Map<DrasylAddress, PeerRtt> peers();
}
