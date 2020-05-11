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

import org.drasyl.core.common.message.action.LeaveMessageAction;
import org.drasyl.core.common.message.action.MessageAction;

/**
 * A message representing a termination of a connection.
 */
public class LeaveMessage extends AbstractMessage<LeaveMessage> implements UnrestrictedPassableMessage {
    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o);
    }

    @Override
    public String toString() {
        return "LeaveMessage{" +
                "id='" + id + '\'' +
                ", signature=" + signature +
                '}';
    }

    @Override
    public MessageAction<LeaveMessage> getAction() {
        return new LeaveMessageAction(this);
    }
}
