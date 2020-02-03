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

package org.drasyl.all.util.listener;

import static org.junit.Assert.assertEquals;

import org.junit.jupiter.api.Test;

import org.drasyl.all.messages.Message;
import org.drasyl.all.messages.Status;

public class ResponseListenerTest {
    @Test
    public void instantiationTest() {
        Message msg = Status.OK;

        IResponseListener<Message> listener = new ResponseListener() {
            @Override
            public void emitEvent(Message message) {
                assertEquals(msg, message);
            }
        };

        listener.emitEvent(msg);
    }
}
