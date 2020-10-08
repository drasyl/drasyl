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

package org.drasyl.peer.connection.intravm;

import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.peer.Path;
import org.drasyl.peer.PeerInformation;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.Pipeline;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;

@ExtendWith(MockitoExtension.class)
class IntraVmDiscoveryTest {
    @Mock
    private CompressedPublicKey publicKey;
    @Mock
    private Pipeline pipeline;
    @Mock
    private PeersManager peersManager;
    @Mock
    private Path path;
    @Mock
    private PeerInformation peerInformation;

    @Nested
    class Open {
        @Test
        void shouldAddToDiscoveries() {
            try (final IntraVmDiscovery underTest = new IntraVmDiscovery(publicKey, peersManager, path, peerInformation, new AtomicBoolean(false))) {
                underTest.open();

                assertThat(IntraVmDiscovery.discoveries, aMapWithSize(1));
            }
        }
    }

    @Nested
    class Close {
        @Test
        void shouldRemovePeerInformation() {
            try (final IntraVmDiscovery underTest = new IntraVmDiscovery(publicKey, peersManager, path, peerInformation, new AtomicBoolean(true))) {
                underTest.close();

                assertThat(IntraVmDiscovery.discoveries, aMapWithSize(0));
            }
        }
    }
}