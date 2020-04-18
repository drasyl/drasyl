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

package city.sane.relay.server.monitoring.models;

import java.util.ArrayList;
import java.util.Collection;

public class InternalServerState extends InternalState {
    private Collection<RemoteClientState> remoteClients;
    private Collection<InternalClientState> localClients;
    private Collection<InternalClientState> relays;
    private Collection<String> deadClients;

    private long pendingFutures;
    private long timeoutedFutures;
    private long bootTime;

    private String systemUID;
    private String configs;
    private String ip;

    public InternalServerState() {
        remoteClients = new ArrayList<>();
        localClients = new ArrayList<>();
        relays = new ArrayList<>();
        deadClients = new ArrayList<>();
    }

    /**
     * @return the localClients
     */
    public Collection<InternalClientState> getLocalClients() {
        return localClients;
    }

    /**
     * @param localClients the localClients to set
     */
    public void setLocalClients(Collection<InternalClientState> localClients) {
        this.localClients = localClients;
    }

    /**
     * @return the remoteClients
     */
    public Collection<RemoteClientState> getRemoteClients() {
        return remoteClients;
    }

    /**
     * @param remoteClients the remoteClients to set
     */
    public void setRemoteClients(Collection<RemoteClientState> remoteClients) {
        this.remoteClients = remoteClients;
    }

    /**
     * @return the relays
     */
    public Collection<InternalClientState> getRelays() {
        return relays;
    }

    /**
     * @param relays the relays to set
     */
    public void setRelays(Collection<InternalClientState> relays) {
        this.relays = relays;
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
     * @return the systemUID
     */
    public String getSystemUID() {
        return systemUID;
    }

    /**
     * @param systemUID the systemUID to set
     */
    public void setSystemUID(String systemUID) {
        this.systemUID = systemUID;
    }

    /**
     * @return the configs
     */
    public String getConfigs() {
        return configs;
    }

    /**
     * @param configs the configs to set
     */
    public void setConfigs(String configs) {
        this.configs = configs;
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

    public Collection<String> getDeadClients() {
        return deadClients;
    }

    public void setDeadClients(Collection<String> deadClients) {
        this.deadClients = deadClients;
    }
}
