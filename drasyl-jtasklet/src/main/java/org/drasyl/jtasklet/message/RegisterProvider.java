package org.drasyl.jtasklet.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import static java.util.Objects.requireNonNull;
import static org.drasyl.util.Preconditions.requireNonNegative;

public class RegisterProvider implements TaskletMessage {
    private final long benchmark;
    private final String token;

    @JsonCreator
    public RegisterProvider(@JsonProperty("benchmark") final long benchmark,
                            @JsonProperty("token") final String token) {
        this.benchmark = requireNonNegative(benchmark);
        this.token = requireNonNull(token);
    }

    public long getBenchmark() {
        return benchmark;
    }

    public String getToken() {
        return token;
    }

    @Override
    public String toString() {
        return "RegisterProvider{" +
                "benchmark=" + benchmark +
                ", token='" + token + '\'' +
                '}';
    }
}
