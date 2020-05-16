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
package org.drasyl.peer.connection;

import org.drasyl.peer.connection.server.NodeServerClientConnection;
import org.drasyl.peer.connection.superpeer.SuperPeerConnection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Compares two {@link PeerConnection}'s by their path length in the connectivity graph.
 */
public class ConnectionComparator implements Comparator<PeerConnection> {
    public static final ConnectionComparator INSTANCE = new ConnectionComparator();
    private static final List<Class<?>> PRIORITISATION = new ArrayList<>();

    static {
        PRIORITISATION.add(LoopbackPeerConnection.class);
        PRIORITISATION.add(NodeServerClientConnection.class);
        PRIORITISATION.add(SuperPeerConnection.class);
        PRIORITISATION.add(AbstractPeerConnection.class);
        PRIORITISATION.add(PeerConnection.class);
    }

    private ConnectionComparator() {
    }

    // TODO: Use more sophisticated algorithms to determine the shortest path https://git.informatik.uni-hamburg.de/sane-public/drasyl/-/issues/28
    @Override
    public int compare(PeerConnection first,
                       PeerConnection second) {
        int firstIndex = -1;
        int secondIndex = -1;

        for (int i = 0; i < PRIORITISATION.size(); i++) {
            if (firstIndex == -1 && PRIORITISATION.get(i).isAssignableFrom(first.getClass())) {
                firstIndex = i;
            }

            if (secondIndex == -1 && PRIORITISATION.get(i).isAssignableFrom(second.getClass())) {
                secondIndex = i;
            }
        }

        return Integer.compare(firstIndex, secondIndex);
    }
}
