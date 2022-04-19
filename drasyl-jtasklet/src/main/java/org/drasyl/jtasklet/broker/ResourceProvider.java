package org.drasyl.jtasklet.broker;

import org.drasyl.handler.PeersRttReport;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.RandomUtil;

import java.util.Objects;

import static org.drasyl.jtasklet.broker.ResourceProvider.ProviderState.ASSIGNED;
import static org.drasyl.jtasklet.broker.ResourceProvider.ProviderState.EXECUTED;
import static org.drasyl.jtasklet.broker.ResourceProvider.ProviderState.OFFLOADED;
import static org.drasyl.jtasklet.broker.ResourceProvider.ProviderState.READY;
import static org.drasyl.jtasklet.broker.ResourceProvider.ProviderState.EXECUTING;

public class ResourceProvider {
    private final long benchmark;
    private ProviderState providerState;
    private long stateTime;
    private DrasylAddress assignedTo;
    private PeersRttReport rttReport;
    private String token;
    private int succeededTasks;
    private int failedTasks;

    public ResourceProvider(final long benchmark) {
        this.benchmark = benchmark;
        this.providerState = READY;
        this.stateTime = System.currentTimeMillis();
    }

    public String assigned(final DrasylAddress assignedTo) {
        this.providerState = ASSIGNED;
        this.stateTime = System.currentTimeMillis();
        this.assignedTo = Objects.requireNonNull(assignedTo);
        this.token = RandomUtil.randomString(6);
        return token;
    }

    public void offloaded() {
        switch (providerState) {
            case ASSIGNED:
                this.providerState = OFFLOADED;
                this.stateTime = System.currentTimeMillis();
                break;

            default:
                // do nothing
        }
    }

    public void executing() {
        switch (providerState) {
            case ASSIGNED:
            case OFFLOADED:
                this.providerState = EXECUTING;
                this.stateTime = System.currentTimeMillis();

                break;

            default:
                // do nothing
        }
    }

    public void executed() {
        switch (providerState) {
            case ASSIGNED:
            case OFFLOADED:
            case EXECUTING:
                this.providerState = EXECUTED;
                this.stateTime = System.currentTimeMillis();
                break;

            default:
                // do nothing
        }
    }

    public void done() {
        this.providerState = READY;
        this.stateTime = System.currentTimeMillis();
        this.succeededTasks++;
        this.token = null;
        this.assignedTo = null;
    }

    public void reset() {
        this.providerState = READY;
        this.stateTime = System.currentTimeMillis();
        this.failedTasks++;
        this.token = null;
        this.assignedTo = null;
    }

    @Override
    public String toString() {
        return "TaskletVm{" +
                "benchmark=" + benchmark +
                "rttReport=" + rttReport +
                ", token=" + token +
                '}';
    }

    public int succeededTasks() {
        return succeededTasks;
    }

    public int failedTasks() {
        return failedTasks;
    }

    public float errorRate() {
        if (succeededTasks > 0) {
            return (float) failedTasks / succeededTasks;
        }
        else {
            return 0;
        }
    }

    public long timeSinceLastStateChange() {
        return System.currentTimeMillis() - stateTime;
    }

    public long benchmark() {
        return benchmark;
    }

    public String token() {
        return token;
    }

    public ProviderState state() {
        return providerState;
    }

    public DrasylAddress assignedTo() {
        return assignedTo;
    }

    public boolean isAssignedTo(final DrasylAddress sender, final String token) {
        return Objects.equals(assignedTo, sender) && this.token != null && Objects.equals(this.token, token);
    }

    public enum ProviderState {
        READY,
        ASSIGNED,
        OFFLOADED,
        EXECUTING,
        EXECUTED
    }
}
