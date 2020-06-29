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
package org.drasyl.peer.connection.message;

import org.drasyl.crypto.Crypto;

import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * Message that represents a message from one node to another one.
 */
public abstract class AbstractMessage implements Message {
    protected final String id;

    public AbstractMessage() {
        this(Crypto.randomString(12));
    }

    protected AbstractMessage(String id) {
        this.id = requireNonNull(id);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(42);
    }

    @Override
    public boolean equals(Object o) {
        return o != null && getClass() == o.getClass();
    }

    @Override
    public String toString() {
        return "AbstractMessage{" +
                "id='" + id +
                '}';
    }
}
