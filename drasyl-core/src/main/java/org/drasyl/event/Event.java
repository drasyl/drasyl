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
package org.drasyl.event;

import org.drasyl.identity.Identity;
import org.drasyl.util.Pair;

import java.util.Objects;

/**
 * Describes an Event that provides the application with information about the local node, other
 * peers, connections or incoming messages.
 */
public class Event {
    private final EventCode code;
    private final Node node;
    private final Peer peer;
    private final Pair<Identity, byte[]> message;

    public Event(EventCode code, Node node, Peer peer, Pair<Identity, byte[]> message) {
        this.code = code;
        this.node = node;
        this.peer = peer;
        this.message = message;
    }

    public Event(EventCode code, Node node) {
        if (!code.isNodeEvent()) {
            throw new IllegalArgumentException("Given code must refer to a node!");
        }

        this.code = code;
        this.node = node;
        peer = null;
        message = null;
    }

    public Event(EventCode code, Peer peer) {
        if (!code.isPeerEvent()) {
            throw new IllegalArgumentException("Given code must refer to a peer!");
        }

        this.code = code;
        node = null;
        this.peer = peer;
        message = null;
    }

    public Event(EventCode code, Pair<Identity, byte[]> message) {
        if (!code.isMessageEvent()) {
            throw new IllegalArgumentException("Given code must refer to a message!");
        }

        this.code = code;
        node = null;
        peer = null;
        this.message = message;
    }

    public EventCode getCode() {
        return code;
    }

    public Node getNode() {
        return node;
    }

    public Peer getPeer() {
        return peer;
    }

    public Pair<Identity, byte[]> getMessage() {
        return message;
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, node, peer, message);
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
                Objects.equals(message, event.message);
    }

    @Override
    public String toString() {
        return "Event{" +
                "code=" + code +
                ", node=" + node +
                ", peer=" + peer +
                ", message=" + message +
                '}';
    }
}
