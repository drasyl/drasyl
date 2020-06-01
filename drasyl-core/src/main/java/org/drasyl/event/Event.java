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
    private final EventType type;
    private final Node node;
    private final Peer peer;
    private final Pair<Identity, byte[]> message;

    Event(EventType type, Node node, Peer peer, Pair<Identity, byte[]> message) {
        this.type = type;
        this.node = node;
        this.peer = peer;
        this.message = message;
    }

    public Event(EventType type, Node node) {
        if (!type.isNodeEvent()) {
            throw new IllegalArgumentException("Given code must refer to a node!");
        }

        this.type = type;
        this.node = node;
        peer = null;
        message = null;
    }

    public Event(EventType type, Peer peer) {
        if (!type.isPeerEvent()) {
            throw new IllegalArgumentException("Given code must refer to a peer!");
        }

        this.type = type;
        node = null;
        this.peer = peer;
        message = null;
    }

    public Event(EventType type, Pair<Identity, byte[]> message) {
        if (!type.isMessageEvent()) {
            throw new IllegalArgumentException("Given code must refer to a message!");
        }

        this.type = type;
        node = null;
        peer = null;
        this.message = message;
    }

    public EventType getType() {
        return type;
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
        return Objects.hash(type, node, peer, message);
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
        return type == event.type &&
                Objects.equals(node, event.node) &&
                Objects.equals(peer, event.peer) &&
                Objects.equals(message, event.message);
    }

    @Override
    public String toString() {
        return "Event{" +
                "code=" + type +
                ", node=" + node +
                ", peer=" + peer +
                ", message=" + message +
                '}';
    }
}
