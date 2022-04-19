package org.drasyl.jtasklet.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class RegisterProvider implements TaskletMessage {
    private final long benchmark;

    @JsonCreator
    public RegisterProvider(@JsonProperty("benchmark") final long benchmark) {
        this.benchmark = benchmark;
    }

    public long getBenchmark() {
        return benchmark;
    }

    @Override
    public String toString() {
        return "RegisterProvider{" +
                "benchmark=" + benchmark +
                '}';
    }
}
