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

package org.drasyl.all.monitoring.models;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WebsocketRequestTest {
    private String action;
    private String token;

    @BeforeEach
    void setUp() {
        action = "action";
        token = "token";
    }

    @Test
    void testObjectCreation() {
        WebsocketRequest request = new WebsocketRequest(token, action);

        assertEquals(token, request.getToken());
        assertEquals(action, request.getAction());
    }

    @Test
    void testObjectCreationNull() {
        WebsocketRequest request = new WebsocketRequest();

        assertNull(request.getToken());
        assertNull(request.getAction());
    }
}