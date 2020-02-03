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

package org.drasyl.all.filters;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.drasyl.all.models.SessionUID;
import org.drasyl.all.util.random.RandomUtil;

/**
 * Helper class to filter client UIDs.
 */
public final class ClientFilter {
    private ClientFilter() {
    }

    /**
     * Filters a set of client UIDs, filtered for the given address.
     *
     * @param senderUID   the sender's UID that should be used for filtering
     * @param receiverUID the receiver's UID that should be used for filtering
     * @param clientUIDs  the set of client UIDs to filter
     * @return a filtered copy of the given set
     */
    public static Set<SessionUID> filter(SessionUID senderUID, SessionUID receiverUID, Set<SessionUID> clientUIDs) {
        Set<SessionUID> filteredClientUIDs = new HashSet<>();

        for (SessionUID suid : receiverUID.getUIDs()) {
            if (!clientUIDs.isEmpty()) {
                if (Objects.equals(suid, SessionUID.ALL)) {
                    filteredClientUIDs = new HashSet<>(clientUIDs);
                    filteredClientUIDs.removeAll(receiverUID.getUIDs());
                    break;
                } else if (Objects.equals(receiverUID, SessionUID.ANY)) {
                    SessionUID clientUID = getAny(senderUID, clientUIDs);

                    if (clientUID != null)
                        filteredClientUIDs.add(clientUID);
                } else if (clientUIDs.contains(suid)) {
                    filteredClientUIDs.add(suid);
                }
            }
        }

        return filteredClientUIDs;
    }

    /**
     * Filters a set of client UIDs, filtered for the given address and returns any
     * receiver but not the sender.
     *
     * @param sender     the sender's UID that should be used for filtering
     * @param clientUIDs the set of client UIDs
     * @return a receiver UID or null if only the sender exists
     */
    public static SessionUID getAny(SessionUID sender, Collection<SessionUID> clientUIDs) {
        SessionUID clientUID = null;
        do {
            Optional<SessionUID> option =
                    clientUIDs.stream().skip(RandomUtil.randomNumber(clientUIDs.size())).findFirst();

            if (option.isPresent()) {
                clientUID = option.get();
            }
        } while (clientUIDs.size() >= 2 && clientUID != null && clientUID.equals(sender));

        if (clientUID != null && !clientUID.equals(sender))
            return clientUID;
        return null;
    }
}
