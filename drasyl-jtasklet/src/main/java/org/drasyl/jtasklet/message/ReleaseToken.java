package org.drasyl.jtasklet.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

import static java.util.Objects.requireNonNull;

public class ReleaseToken implements TaskletMessage {
    private final String token;

    @JsonCreator
    public ReleaseToken(@JsonProperty("token") final String token) {
        this.token = requireNonNull(token);
    }

    public String getToken() {
        return token;
    }
}
