/*
 * Copyright (c) 2020
 *
 * This file is part of drasyl.
 *
 * drasyl is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * drasyl is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.drasyl.all.monitoring.models;

import java.util.Collection;
import java.util.HashSet;

public class RemoteClientState {
    private String uid;

    private Collection<String> channels;

    public RemoteClientState() {
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
