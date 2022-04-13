package org.drasyl.jtasklet.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.drasyl.handler.PeersRttReport;

public class VmHeartbeat implements TaskletMessage {
    private final long benchmark;
    private final PeersRttReport rttReport;
    private final String token;

    @JsonCreator
    public VmHeartbeat(@JsonProperty("benchmark") final long benchmark,
                       @JsonProperty("report") final PeersRttReport rttReport,
                       @JsonProperty("token") final String token) {
        this.benchmark = benchmark;
        this.rttReport = rttReport;
        this.token = token;
    }

    public long getBenchmark() {
        return benchmark;
    }

    public PeersRttReport getRttReport() {
        return rttReport;
    }

    public String getToken() {
        return token;
    }

    @Override
    public String toString() {
        return "VmHeartbeat{" +
                "benchmark=" + benchmark +
                ", rttReport=" + rttReport +
                ", token=" + token +
                '}';
    }
}
