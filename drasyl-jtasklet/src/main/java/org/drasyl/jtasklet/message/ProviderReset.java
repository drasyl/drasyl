package org.drasyl.jtasklet.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import static java.util.Objects.requireNonNull;

public class ProviderReset implements TaskletMessage {
    private final String newToken;

    @JsonCreator
    public ProviderReset(@JsonProperty("token") final String newToken) {
        this.newToken = requireNonNull(newToken);
    }

    @Override
    public String toString() {
        return "ProviderReset{" +
                "newToken='" + newToken + '\'' +
                '}';
    }

    public String getNewToken() {
        return newToken;
    }
}
