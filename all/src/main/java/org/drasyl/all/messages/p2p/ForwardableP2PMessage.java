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

package org.drasyl.all.messages.p2p;

import org.drasyl.all.messages.ForwardableMessage;

import java.util.Objects;

import static java.util.Objects.requireNonNull;

public class ForwardableP2PMessage extends P2PMessage {
    private final ForwardableMessage message;

    ForwardableP2PMessage() {
        message = null;
    }

    /**
     * Creates a new immutable message object that is forwarded between peers of the
     * relay server network.
     * 
     * @param message forwardable message
     */
    public ForwardableP2PMessage(ForwardableMessage message) {
        this.message = requireNonNull(message);
    }

    /**
     * @return the message
     */
    public ForwardableMessage getMessage() {
        return message;
    }

    @Override
    public int hashCode() {
        return Objects.hash(message);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ForwardableP2PMessage) {
            ForwardableP2PMessage res2 = (ForwardableP2PMessage) obj;

            return Objects.equals(message, res2.message);
        }

        return false;
    }

    @Override
    public String toString() {
        return "ForwardableP2PMessage [message=" + getMessage().toFullString() + "]";
    }
}
