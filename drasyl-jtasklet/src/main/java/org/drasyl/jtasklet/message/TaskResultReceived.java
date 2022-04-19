package org.drasyl.jtasklet.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import static java.util.Objects.requireNonNull;

public class TaskResultReceived implements TaskletMessage {
    private final String token;

    @JsonCreator
    public TaskResultReceived(@JsonProperty("token") final String token) {
        this.token = requireNonNull(token);
    }

    @Override
    public String toString() {
        return "TaskResultReceived{" +
                "token='" + token + '\'' +
                '}';
    }

    public String getToken() {
        return token;
    }
}
