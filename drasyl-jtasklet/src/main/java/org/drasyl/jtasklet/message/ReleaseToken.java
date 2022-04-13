package org.drasyl.jtasklet.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.drasyl.identity.IdentityPublicKey;

import static java.util.Objects.requireNonNull;

public class ReleaseToken implements TaskletMessage {
    private final IdentityPublicKey vm;

    @JsonCreator
    public ReleaseToken(@JsonProperty("vm") final IdentityPublicKey vm) {
        this.vm = requireNonNull(vm);
    }

    public IdentityPublicKey getVm() {
        return vm;
    }
}
