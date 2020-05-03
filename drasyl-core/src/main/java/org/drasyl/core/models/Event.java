/*
 * Copyright (c) 2020.
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
package org.drasyl.core.models;

import java.util.Arrays;
import java.util.Objects;

public class Event {
    private final Code code;
    private final Node node;
    private final Peer peer;
    private final byte[] message;

    public Event(Code code, Node node, Peer peer, byte[] message) {
        this.code = code;
        this.node = node;
        this.peer = peer;
        this.message = message;
    }

    public Event(Code code, Node node) {
        if (!code.isNodeEvent()) {
            throw new IllegalArgumentException("Given code must refer to a node!");
        }

        this.code = code;
        this.node = node;
        peer = null;
        message = null;
    }

    public Event(Code code, Peer peer) {
        if (!code.isPeerEvent()) {
            throw new IllegalArgumentException("Given code must refer to a peer!");
        }

        this.code = code;
        node = null;
        this.peer = peer;
        message = null;
    }

    public Event(Code code, byte[] message) {
        if (!code.isMessageEvent()) {
            throw new IllegalArgumentException("Given code must refer to a message!");
        }

        this.code = code;
        node = null;
        peer = null;
        this.message = message;
    }

    public Code getCode() {
        return code;
    }

    public Node getNode() {
        return node;
    }

    public Peer getPeer() {
        return peer;
    }

    public byte[] getMessage() {
        return message;
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(code, node, peer);
        result = 31 * result + Arrays.hashCode(message);
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Event event = (Event) o;
        return code == event.code &&
                Objects.equals(node, event.node) &&
                Objects.equals(peer, event.peer) &&
                Arrays.equals(message, event.message);
    }

    @Override
    public String toString() {
        return "Event{" +
                "code=" + code +
                ", node=" + node +
                ", peer=" + peer +
                ", message=" + Arrays.toString(message) +
                '}';
    }
}
