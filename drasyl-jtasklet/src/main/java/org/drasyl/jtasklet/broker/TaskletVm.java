package org.drasyl.jtasklet.broker;

import org.drasyl.handler.PeersRttReport;

import java.util.Objects;

public class TaskletVm {
    private long benchmark;
    private long lastHeartbeatTime;
    private PeersRttReport rttReport;
    private boolean busy;
    private int computations = 0;
    private String token;

    public TaskletVm(final long benchmark) {
        this.benchmark = benchmark;
    }

    public void heartbeatReceived(final PeersRttReport rttReport, final String token, final long benchmark) {
        this.rttReport = rttReport;
        lastHeartbeatTime = System.currentTimeMillis();
        if (!Objects.equals(this.token, token)) {
            busy = false;
        }
        this.token = token;
        this.benchmark = benchmark;
    }

    @Override
    public String toString() {
        return "TaskletVm{" +
                "benchmark=" + benchmark +
                "rttReport=" + rttReport +
                ", stale=" + isStale() +
                ", busy=" + isBusy() +
                ", token=" + token +
                '}';
    }

    public boolean isStale() {
        return timeSinceLastHeartbeat() >= 5_000L;
    }

    public long timeSinceLastHeartbeat() {
        return System.currentTimeMillis() - lastHeartbeatTime;
    }

    public boolean isBusy() {
        return busy;
    }

    public void markBusy() {
        this.busy = true;
    }

    public int getComputations() {
        return computations;
    }

    public long getBenchmark() {
        return benchmark;
    }

    public String getToken() {
        return token;
    }

    public void markIdle(final String token) {
        this.busy = false;
        this.computations++;
        this.token = token;
    }
}
