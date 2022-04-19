package org.drasyl.jtasklet.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import static java.util.Objects.requireNonNull;

public class TaskExecuted implements TaskletMessage {
    private final String token;

    @JsonCreator
    public TaskExecuted(@JsonProperty("token") final String token) {
        this.token = requireNonNull(token);
    }

    @Override
    public String toString() {
        return "TaskExecuted{" +
                "token='" + token + '\'' +
                '}';
    }

    public String getToken() {
        return token;
    }
}
