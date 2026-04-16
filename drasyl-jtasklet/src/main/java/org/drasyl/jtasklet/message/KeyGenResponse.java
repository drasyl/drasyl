package org.drasyl.jtasklet.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;

public class KeyGenResponse implements TaskletMessage {
    private final List<String> clientIds;
    private final Map<String, String> clientKeys;
    private final int boundX;
    private final int boundY;
    private final int boundN;

    @JsonCreator
    public KeyGenResponse(@JsonProperty("clientIds") final List<String> clientIds,
                          @JsonProperty("clientKeys") final Map<String, String> clientKeys,
                          @JsonProperty("boundX") final int boundX,
                          @JsonProperty("boundY") final int boundY,
                          @JsonProperty("boundN") final int boundN) {
        this.clientIds = requireNonNull(clientIds);
        this.clientKeys = requireNonNull(clientKeys);
        this.boundX = boundX;
        this.boundY = boundY;
        this.boundN = boundN;
    }

    @Override
    public String toString() {
        return "KeyGenResponse{clientIds=List[" + clientIds.size() + "], clientKeys=Map[" + clientKeys.size() + "]}";
    }

    public List<String> getClientIds() {
        return clientIds;
    }

    public Map<String, String> getClientKeys() {
        return clientKeys;
    }

    public int getBoundX() {
        return boundX;
    }

    public int getBoundY() {
        return boundY;
    }

    public int getBoundN() {
        return boundN;
    }
}
