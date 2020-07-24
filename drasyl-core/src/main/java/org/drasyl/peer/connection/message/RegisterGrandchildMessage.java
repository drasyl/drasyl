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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.drasyl.identity.CompressedPublicKey;

import java.util.Set;

/**
 * Used to register grandchildren at a super peer.
 */
public class RegisterGrandchildMessage extends AbstractGrandchildMessage {
    @JsonCreator
    private RegisterGrandchildMessage(@JsonProperty("id") MessageId id,
                                      @JsonProperty("grandchildren") Set<CompressedPublicKey> grandchildren) {
        super(id, grandchildren);
    }

    /**
     * Creates a new register grandchild message.
     *
     * @param grandchildren the grandchildren
     */
    public RegisterGrandchildMessage(Set<CompressedPublicKey> grandchildren) {
        super(grandchildren);
    }

    @Override
    public String toString() {
        return "RegisterGrandchildMessage{" +
                "grandchildren=" + grandchildren +
                ", id='" + id +
                '}';
    }
}
