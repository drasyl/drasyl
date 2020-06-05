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
package org.drasyl.peer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.util.Set;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PeerInformationTest {
    @Mock
    private Set<URI> endpoints;
    @Mock
    private Set<Path> paths;
    private PeerInformation underTest;

    @BeforeEach
    void setUp() {
        underTest = new PeerInformation(endpoints, paths);
    }

    @Nested
    class Add {
        @Mock
        private PeerInformation peerInformation;

        @Test
        void shouldAddNewInformationToExistingInformation() {
            underTest.add(peerInformation);

            verify(endpoints).addAll(peerInformation.getEndpoints());
            verify(paths).addAll(peerInformation.getPaths());
        }
    }

    @Nested
    class Remove {
        @Mock
        private PeerInformation peerInformation;

        @Test
        void shouldRemoveInformationFromExistingInformation() {
            underTest.remove(peerInformation);

            verify(endpoints).removeAll(peerInformation.getEndpoints());
            verify(paths).removeAll(peerInformation.getPaths());
        }
    }
}