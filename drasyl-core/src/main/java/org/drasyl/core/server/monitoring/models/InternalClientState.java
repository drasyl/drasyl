/*
 * Copyright (c) 2020
 *
 * This file is part of Relayserver.
 *
 * Relayserver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Relayserver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Relayserver.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.drasyl.core.server.monitoring.models;

import java.util.Collection;
import java.util.HashSet;

public class InternalClientState extends InternalState {
    private String uid;
    private String ip;

    private long bootTime;

    private boolean initialized;
    private boolean terminated;


    private long pendingFutures;
    private long timeoutedFutures;

    private Collection<String> channels;

    public InternalClientState() {
        channels = new HashSet<>();
    }

    /**
     * @return the uid
     */
    public String getUID() {
        return uid;
    }

    /**
     * @param uid the uid to set
     */
    public void setUID(String uid) {
        this.uid = uid;
    }

    /**
     * @return the ip
     */
    public String getIP() {
        return ip;
    }

    /**
     * @param ip the ip to set
     */
    public void setIP(String ip) {
        this.ip = ip;
    }

    /**
     * @return the bootTime
     */
    public long getBootTime() {
        return bootTime;
    }

    /**
     * @param bootTime the bootTime to set
     */
    public void setBootTime(long bootTime) {
        this.bootTime = bootTime;
    }

    /**
     * @return the initialized
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * @param initialized the initialized to set
     */
    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }

    /**
     * @return the terminated
     */
    public boolean isTerminated() {
        return terminated;
    }

    /**
     * @param terminated the terminated to set
     */
    public void setTerminated(boolean terminated) {
        this.terminated = terminated;
    }

    /**
     * @return the pendingFutures
     */
    public long getPendingFutures() {
        return pendingFutures;
    }

    /**
     * @param pendingFutures the pendingFutures to set
     */
    public void setPendingFutures(long pendingFutures) {
        this.pendingFutures = pendingFutures;
    }

    /**
     * @return the timeoutedFutures
     */
    public long getTimeoutedFutures() {
        return timeoutedFutures;
    }

    /**
     * @param timeoutedFutures the timeoutedFutures to set
     */
    public void setTimeoutedFutures(long timeoutedFutures) {
        this.timeoutedFutures = timeoutedFutures;
    }

    /**
     * @return the channels
     */
    public Collection<String> getChannels() {
        return channels;
    }

    /**
     * @param channels the channels to set
     */
    public void setChannels(Collection<String> channels) {
        this.channels = channels;
    }
}
