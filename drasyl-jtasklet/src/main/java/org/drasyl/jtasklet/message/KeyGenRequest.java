package org.drasyl.jtasklet.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

import static java.util.Objects.requireNonNull;

public class KeyGenRequest implements TaskletMessage {
    private final List<String> clientIds;
    private final int vecLen;

    @JsonCreator
    public KeyGenRequest(@JsonProperty("clientIds") final List<String> clientIds,
                         @JsonProperty("vecLen") final int vecLen) {
        this.clientIds = requireNonNull(clientIds);
        this.vecLen = vecLen;
    }

    @Override
    public String toString() {
        return "KeyGenRequest{clientIds=List[" + clientIds.size() + "], vecLen=" + vecLen + '}';
    }

    public List<String> getClientIds() {
        return clientIds;
    }

    public int getVecLen() {
        return vecLen;
    }
}
