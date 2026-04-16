package org.drasyl.jtasklet.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import static java.util.Objects.requireNonNull;

public class DerivedKeyResponse implements TaskletMessage {
    private final String functionalKey;

    @JsonCreator
    public DerivedKeyResponse(@JsonProperty("functionalKey") final String functionalKey) {
        this.functionalKey = requireNonNull(functionalKey);
    }

    @Override
    public String toString() {
        return "DerivedKeyResponse{functionalKey=***}";
    }

    public String getFunctionalKey() {
        return functionalKey;
    }
}
