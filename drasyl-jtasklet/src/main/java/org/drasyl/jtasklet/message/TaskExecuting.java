package org.drasyl.jtasklet.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import static java.util.Objects.requireNonNull;

public class TaskExecuting implements TaskletMessage {
    private final String token;

    @JsonCreator
    public TaskExecuting(@JsonProperty("token") final String token) {
        this.token = requireNonNull(token);
    }

    @Override
    public String toString() {
        return "TaskExecuting{" +
                "token='" + token + '\'' +
                '}';
    }

    public String getToken() {
        return token;
    }
}
