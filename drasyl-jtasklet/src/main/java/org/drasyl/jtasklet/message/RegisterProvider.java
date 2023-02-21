package org.drasyl.jtasklet.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Arrays;

import static java.util.Objects.requireNonNull;
import static org.drasyl.util.Preconditions.requireNonNegative;

public class RegisterProvider implements TaskletMessage {
    private final long benchmark;
    private final String token;
    private final String[] tags;

    @JsonCreator
    public RegisterProvider(@JsonProperty("benchmark") final long benchmark,
                            @JsonProperty("token") final String token,
                            @JsonProperty("tags") final String[] tags) {
        this.benchmark = requireNonNegative(benchmark);
        this.token = requireNonNull(token);
        this.tags = requireNonNull(tags);
    }

    public long getBenchmark() {
        return benchmark;
    }

    public String getToken() {
        return token;
    }

    public String[] getTags() {
        return tags;
    }

    @Override
    public String toString() {
        return "RegisterProvider{" +
                "benchmark=" + benchmark +
                ", token='" + token + '\'' +
                ", tags='" + Arrays.toString(tags) + '\'' +
                '}';
    }
}
