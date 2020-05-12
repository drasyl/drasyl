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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.drasyl.core.common.message.action.LeaveMessageAction;
import org.drasyl.core.common.message.action.MessageAction;

import java.util.Objects;

/**
 * A message representing a termination of a connection.
 */
public class LeaveMessage extends AbstractMessage<LeaveMessage> implements UnrestrictedPassableMessage {
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private final String reason;

    public LeaveMessage() {
        this("");
    }

    public LeaveMessage(String reason) {
        this.reason = reason;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        LeaveMessage that = (LeaveMessage) o;
        return Objects.equals(reason, that.reason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), reason);
    }

    @Override
    public String toString() {
        return "LeaveMessage{" +
                "reason='" + reason + '\'' +
                ", id='" + id + '\'' +
                ", signature=" + signature +
                '}';
    }

    public String getReason() {
        return reason;
    }

    @Override
    public MessageAction<LeaveMessage> getAction() {
        return new LeaveMessageAction(this);
    }
}
