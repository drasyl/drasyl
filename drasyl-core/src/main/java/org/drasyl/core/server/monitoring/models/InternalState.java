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

public abstract class InternalState {
    private String ua;

    private long totalReceivedMessages;
    private long totalFailedMessages;
    private long totalSentMessages;

    /**
     * @return the ua
     */
    public String getUA() {
        return ua;
    }

    /**
     * @param ua the ua to set
     */
    public void setUA(String ua) {
        this.ua = ua;
    }

    public long getTotalFailedMessages() {
        return totalFailedMessages;
    }

    public void setTotalFailedMessages(long totalFailedMessages) {
        this.totalFailedMessages = totalFailedMessages;
    }

    public long getTotalSentMessages() {
        return totalSentMessages;
    }

    public void setTotalSentMessages(long totalSentMessages) {
        this.totalSentMessages = totalSentMessages;
    }

    public long getTotalReceivedMessages() {
        return totalReceivedMessages;
    }

    public void setTotalReceivedMessages(long totalReceivedMessages) {
        this.totalReceivedMessages = totalReceivedMessages;
    }
}
