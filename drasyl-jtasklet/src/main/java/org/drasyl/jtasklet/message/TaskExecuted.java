package org.drasyl.jtasklet.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import static java.util.Objects.requireNonNull;

public class TaskExecuted implements TaskletMessage {
    private final String token;
    private final String nextToken;

    @JsonCreator
    public TaskExecuted(@JsonProperty("token") final String token,
                        @JsonProperty("nextToken") final String nextToken) {
        this.token = requireNonNull(token);
        this.nextToken = requireNonNull(nextToken);
    }

    @Override
    public String toString() {
        return "TaskExecuted{" +
                "token='" + token + '\'' +
                ", nextToken='" + nextToken + '\'' +
                '}';
    }

    public String getToken() {
        return token;
    }

    public String getNextToken() {
        return nextToken;
    }
}
