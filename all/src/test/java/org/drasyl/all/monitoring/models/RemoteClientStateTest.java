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

import java.util.ArrayList;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

class RemoteClientStateTest {
    private Collection<String> channels;
    private String uid;

    @BeforeEach
    void setUp() {
        channels = new ArrayList<>();
        uid = "uid";
    }

    @Test
    void testObjectCreation() {
        RemoteClientState state = new RemoteClientState();

        state.setChannels(channels);
        state.setUID(uid);

        assertEquals(channels, state.getChannels());
        assertEquals(uid, state.getUID());
    }
}