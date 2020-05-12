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

import org.drasyl.core.common.message.action.MessageAction;
import org.drasyl.core.common.message.action.PongMessageAction;

/**
 * A message representing a PONG response.
 */
public class PongMessage extends AbstractResponseMessage<PingMessage, PongMessage> implements UnrestrictedPassableMessage {
    public PongMessage(String correspondingId) {
        super(correspondingId);
    }

    protected PongMessage() {
        super(null);
    }

    @Override
    public MessageAction<PongMessage> getAction() {
        return new PongMessageAction(this);
    }

    @Override
    public String toString() {
        return "PongMessage{" +
                "correspondingId='" + correspondingId + '\'' +
                ", id='" + id + '\'' +
                ", signature=" + signature +
                '}';
    }
}
