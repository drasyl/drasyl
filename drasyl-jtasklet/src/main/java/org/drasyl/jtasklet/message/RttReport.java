package org.drasyl.jtasklet.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.drasyl.handler.PeersRttHandler.PeersRttReport;

import static java.util.Objects.requireNonNull;

public class RttReport implements TaskletMessage {
    private final PeersRttReport report;

    @JsonCreator
    public RttReport(@JsonProperty("report") final PeersRttReport report) {
        this.report = requireNonNull(report);
    }

    @Override
    public String toString() {
        return "RttReport{" +
                "report=" + report +
                '}';
    }

    public PeersRttReport getReport() {
        return report;
    }
}
