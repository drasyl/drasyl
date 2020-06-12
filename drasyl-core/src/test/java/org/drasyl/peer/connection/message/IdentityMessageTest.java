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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import net.javacrumbs.jsonunit.core.Option;
import org.drasyl.identity.Address;
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeerInformation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class IdentityMessageTest {
    private Address recipient;
    private Identity identity;
    private PeerInformation peerInformation;
    private String correspondingId;
    private short hopCount;

    @BeforeEach
    void setUp() {
        recipient = mock(Address.class);
        identity = mock(Identity.class);
        peerInformation = PeerInformation.of();
        correspondingId = "123";
        hopCount = 64;
    }

    @Test
    void incrementHopCountShouldIncrementHopCountByOne() {
        IdentityMessage message = new IdentityMessage(recipient, identity, peerInformation, correspondingId);

        message.incrementHopCount();

        assertEquals(1, message.getHopCount());
    }
}