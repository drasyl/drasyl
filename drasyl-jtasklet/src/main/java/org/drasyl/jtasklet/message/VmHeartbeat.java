package org.drasyl.jtasklet.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.drasyl.handler.PeersRttReport;

public class VmHeartbeat implements TaskletMessage {
    private final long benchmark;
    private final PeersRttReport rttReport;

    @JsonCreator
    public VmHeartbeat(@JsonProperty("benchmark") final long benchmark,
                       @JsonProperty("report") final PeersRttReport rttReport) {
        this.benchmark = benchmark;
        this.rttReport = rttReport;
    }

    public long getBenchmark() {
        return benchmark;
    }

    public PeersRttReport getRttReport() {
        return rttReport;
    }

    @Override
    public String toString() {
        return "VmHeartbeat{" +
                "benchmark=" + benchmark +
                ", rttReport=" + rttReport +
                '}';
    }
}
