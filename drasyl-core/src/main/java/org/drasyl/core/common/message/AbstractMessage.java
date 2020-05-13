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
package org.drasyl.core.common.message;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.Signature;

import java.util.Objects;

/**
 * Message that represents a message from one node to another one.
 */
public abstract class AbstractMessage<T extends Message<?>> implements Message<T> {
    protected final String id;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    protected Signature signature;

    public AbstractMessage() {
        this(Crypto.randomString(12));
    }

    protected AbstractMessage(String id) {
        Objects.requireNonNull(id);

        this.id = id;
    }

    public Signature getSignature() {
        return signature;
    }

    @Override
    public void setSignature(Signature signature) {
        this.signature = signature;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AbstractMessage<?> that = (AbstractMessage<?>) o;
        return Objects.equals(signature, that.signature);
    }

    @Override
    public int hashCode() {
        return Objects.hash(signature);
    }

    @Override
    public String toString() {
        return "AbstractMessage{" +
                "id='" + id + '\'' +
                ", signature=" + signature +
                '}';
    }
}
