package org.drasyl.jtasklet.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

import static java.util.Objects.requireNonNull;

public class DerivedKeyRequest implements TaskletMessage {
    private final String token;
    private final String session;
    private final List<String> clientIds;
    private final List<List<Integer>> weights;

    @JsonCreator
    public DerivedKeyRequest(@JsonProperty("token") final String token,
                             @JsonProperty("session") final String session,
                             @JsonProperty("clientIds") final List<String> clientIds,
                             @JsonProperty("weights") final List<List<Integer>> weights) {
        this.token = requireNonNull(token);
        this.session = requireNonNull(session);
        this.clientIds = requireNonNull(clientIds);
        this.weights = requireNonNull(weights);
    }

    @Override
    public String toString() {
        return "DerivedKeyRequest{" +
                "token='" + token + '\'' +
                ", session='" + session + '\'' +
                ", clientIds=List[" + clientIds.size() + "]" +
                ", weights=List[" + weights.size() + "]}";
    }

    public String getToken() {
        return token;
    }

    public String getSession() {
        return session;
    }

    public List<String> getClientIds() {
        return clientIds;
    }

    public List<List<Integer>> getWeights() {
        return weights;
    }
}
