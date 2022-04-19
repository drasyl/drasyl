package org.drasyl.jtasklet.broker;

import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.RandomUtil;

import java.util.Objects;

import static java.util.Objects.requireNonNull;
import static org.drasyl.jtasklet.broker.ResourceProvider.ProviderState.ASSIGNED;
import static org.drasyl.jtasklet.broker.ResourceProvider.ProviderState.DONE;
import static org.drasyl.jtasklet.broker.ResourceProvider.ProviderState.EXECUTED;
import static org.drasyl.jtasklet.broker.ResourceProvider.ProviderState.EXECUTING;
import static org.drasyl.jtasklet.broker.ResourceProvider.ProviderState.FAILED;
import static org.drasyl.jtasklet.broker.ResourceProvider.ProviderState.OFFLOADED;
import static org.drasyl.jtasklet.broker.ResourceProvider.ProviderState.READY;

public class ResourceProvider {
    private final long benchmark;
    private ProviderState providerState;
    private long stateTime;
    private DrasylAddress assignedTo;
    private String token;
    private int succeededTasks;
    private int failedTasks;
    private String nextToken;

    public ResourceProvider(final long benchmark, final String token) {
        this.benchmark = benchmark;
        this.token = requireNonNull(token);
        this.providerState = READY;
        this.stateTime = System.currentTimeMillis();
    }

    public boolean taskAssigned(final DrasylAddress assignedTo) {
        switch (providerState) {
            case READY:
                this.providerState = ASSIGNED;
                this.stateTime = System.currentTimeMillis();
                this.assignedTo = requireNonNull(assignedTo);
                return true;

            default:
                return false;
        }
    }

    public boolean taskOffloaded() {
        switch (providerState) {
            case ASSIGNED:
                this.providerState = OFFLOADED;
                this.stateTime = System.currentTimeMillis();
                return true;

            default:
                return false;
        }
    }

    public boolean taskExecuting() {
        switch (providerState) {
            case ASSIGNED:
            case OFFLOADED:
                this.providerState = EXECUTING;
                this.stateTime = System.currentTimeMillis();
                return true;

            default:
                return false;
        }
    }

    public boolean taskExecuted(final String nextToken) {
        switch (providerState) {
            case ASSIGNED:
            case OFFLOADED:
            case EXECUTING:
                this.providerState = EXECUTED;
                this.stateTime = System.currentTimeMillis();
                this.nextToken = nextToken;
                return true;

            case DONE:
                this.providerState = READY;
                this.token = nextToken;
                this.nextToken = null;
                this.stateTime = System.currentTimeMillis();
                this.assignedTo = null;
                return false;

            default:
                return false;
        }
    }

    public boolean taskDone() {
        switch (providerState) {
            case READY:
                return false;

            default:
                if (nextToken != null) {
                    this.providerState = READY;
                    this.token = nextToken;
                    this.nextToken = null;
                    this.assignedTo = null;
                }
                else {
                    this.providerState = DONE;
                }
                this.stateTime = System.currentTimeMillis();
                this.succeededTasks++;
                return true;
        }
    }

    public boolean taskFailed() {
        switch (providerState) {
            case READY:
                return false;

            default:
                this.providerState = FAILED;
                this.stateTime = System.currentTimeMillis();
                this.failedTasks++;
                return true;
        }
    }

    public void providerReset(final String newToken) {
        this.providerState = READY;
        this.stateTime = System.currentTimeMillis();
        this.failedTasks++;
        this.token = newToken;
        this.assignedTo = null;
    }

    @Override
    public String toString() {
        return "ResourceProvider{" +
                "benchmark=" + benchmark +
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
        else if (failedTasks > 0) {
            return 1;
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

    public static String randomToken() {
        return RandomUtil.randomString(6);
    }

    public enum ProviderState {
        READY,
        ASSIGNED,
        OFFLOADED,
        EXECUTING,
        EXECUTED,
        DONE,
        FAILED
    }
}
