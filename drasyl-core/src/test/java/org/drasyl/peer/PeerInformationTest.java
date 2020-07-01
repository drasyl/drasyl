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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PeerInformationTest {
    private Set<URI> endpoints;
    private Set<Path> paths;
    private PeerInformation underTest;

    @BeforeEach
    void setUp() {
        endpoints = Set.of();
        paths = Set.of();
        underTest = new PeerInformation(endpoints, paths);
    }

    @Nested
    class Add {
        @Mock
        private PeerInformation peerInformation;

        @Test
        void shouldAddNewInformationToExistingInformation() {
            underTest.add(peerInformation);

            assertTrue(endpoints.containsAll(peerInformation.getEndpoints()));
            assertTrue(paths.containsAll(peerInformation.getPaths()));
        }
    }

    @Nested
    class Remove {
        @Mock
        private PeerInformation peerInformation;
        @Mock
        private Path path;

        @BeforeEach
        void setUp() {
            endpoints = Set.of(URI.create("ws://local.de"), URI.create("ws://local.com"));
            paths = Set.of(path);
            underTest = new PeerInformation(endpoints, paths);
        }

        @Test
        void shouldRemoveInformationFromExistingInformation() {
            when(peerInformation.getEndpoints()).thenReturn(Set.of(URI.create("ws://local.de")));
            when(peerInformation.getPaths()).thenReturn(Set.of(path));

            underTest.remove(peerInformation);

            assertFalse(underTest.getEndpoints().containsAll(peerInformation.getEndpoints()));
            assertTrue(underTest.getPaths().isEmpty());
        }
    }
}