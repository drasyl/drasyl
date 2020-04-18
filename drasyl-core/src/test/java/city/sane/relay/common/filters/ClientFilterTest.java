/*
 * Copyright (c) 2020
 *
 * This file is part of Relayserver.
 *
 * Relayserver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Relayserver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Relayserver.  If not, see <http://www.gnu.org/licenses/>.
 */

package city.sane.relay.common.filters;

import static city.sane.relay.common.models.SessionUID.of;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.*;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.collections4.SetUtils;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

import city.sane.relay.common.models.SessionUID;

public class ClientFilterTest {

    private final String testClientUID1 = "ClientUID1";
    private final String testClientUID2 = "ClientUID2";
    private final String testClientUID3 = "ClientUID3";
    private final SessionUID sender;
    private final SessionUID testAddressOne;
    private final SessionUID testAddressAll;
    private final SessionUID testAddressAny;
    private final SessionUID testAddressMulticast;
    private final Set<SessionUID> testClientUIDs = SetUtils.hashSet(of(testClientUID1), of(testClientUID2),
            of(testClientUID3));

    public ClientFilterTest() {
        testAddressMulticast = of(testClientUID1, testClientUID2);
        testAddressOne = of(testClientUID1);
        sender = of("ClientUID0");
        testAddressAll = SessionUID.ALL;
        testAddressAny = SessionUID.ANY;
    }

    @Test
    public void filterOneTest() {
        Set<SessionUID> filteredClientUIDs = ClientFilter.filter(sender, testAddressOne, testClientUIDs);

        Assert.assertTrue(filteredClientUIDs.contains(of(testClientUID1)));
        Assert.assertEquals(1, filteredClientUIDs.size());
    }

    @Test
    public void filterMulticastTest() {
        Set<SessionUID> filteredClientUIDs = ClientFilter.filter(sender, testAddressMulticast, testClientUIDs);

        Assert.assertThat(filteredClientUIDs, containsInAnyOrder(of(testClientUID1), of(testClientUID2)));
        Assert.assertEquals(2, filteredClientUIDs.size());
    }

    @Test
    public void filterNoneTest() {
        Collection<SessionUID> filteredClientUIDs = ClientFilter.filter(sender, of("ClientUID4"),
                testClientUIDs);

        Assert.assertTrue(filteredClientUIDs.isEmpty());
    }

    @Test
    public void emptyCollectionTest() {
        Collection<SessionUID> filteredClientUIDs = ClientFilter.filter(sender, testAddressOne, new HashSet<>());

        Assert.assertTrue(filteredClientUIDs.isEmpty());
    }

    @Test
    public void filterAllTest() {
        Collection<SessionUID> filteredClientUIDs = ClientFilter.filter(sender, testAddressAll, testClientUIDs);

        Assert.assertTrue(filteredClientUIDs.containsAll(testClientUIDs));
        Assert.assertEquals(3, filteredClientUIDs.size());
    }

    @Test
    public void filterAnyTest() {
        Collection<SessionUID> filteredClientUIDs = ClientFilter.filter(sender, testAddressAny, testClientUIDs);

        Assert.assertTrue(filteredClientUIDs.contains(of(testClientUID1)) || filteredClientUIDs.contains(of(testClientUID2))
                || filteredClientUIDs.contains(of(testClientUID3)));
        Assert.assertEquals(1, filteredClientUIDs.size());
    }
}
