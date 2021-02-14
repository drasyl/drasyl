/*
 * Copyright (c) 2020-2021.
 *
 * This file is part of drasyl.
 *
 *  drasyl is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  drasyl is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.drasyl.cli.command.perf.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.drasyl.cli.command.perf.PerfClientNode;
import org.drasyl.cli.command.perf.PerfServerNode;

/**
 * Sent from the {@link PerfClientNode} to the {@link PerfServerNode} to request a new session.
 */
public class SessionRequest implements PerfMessage {
    private final int time;
    private final int mps;
    private final int size;
    private final boolean reverse;

    /**
     * @throws IllegalArgumentException if {@code testDuration}, {@code totalMessages} or {@code
     *                                  messageSize} is less than 1
     */
    @JsonCreator
    public SessionRequest(@JsonProperty("time") final int time,
                          @JsonProperty("mps") final int mps,
                          @JsonProperty("size") final int size,
                          @JsonProperty("reverse") final boolean reverse) {
        this.time = time;
        if (time < 1) {
            throw new IllegalArgumentException("time must be greater than 0");
        }
        this.mps = mps;
        if (mps < 1) {
            throw new IllegalArgumentException("mps must be greater than 0");
        }
        this.size = size;
        if (size < 1) {
            throw new IllegalArgumentException("size must be greater than 0");
        }
        this.reverse = reverse;
    }

    public int getMps() {
        return mps;
    }

    public int getTime() {
        return time;
    }

    public int getSize() {
        return size;
    }

    public boolean isReverse() {
        return reverse;
    }

    @Override
    public String toString() {
        return "{" +
                "time=" + time +
                ", mps=" + mps +
                ", size=" + size +
                ", reverse=" + reverse +
                '}';
    }
}
