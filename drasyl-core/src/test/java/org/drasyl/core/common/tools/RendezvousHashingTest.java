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

package org.drasyl.core.common.tools;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.jupiter.api.Test;

import org.drasyl.core.common.tools.dht.RendezvousHashing;

public class RendezvousHashingTest {
    /*
     * input string: murmur hash:
     *
     * VDEVHBvnnLjunit1 -2042016101 VDEVHBvnnLjunit2 -1414930205 VDEVHBvnnLjunit3
     * -868480636
     *
     * AZJhlsszCJjunit2 -387654673 AZJhlsszCJjunit3 788957092 AZJhlsszCJjunit1
     * 1623497235
     *
     * xNvdtbUxrijunit3 -1852717018 xNvdtbUxrijunit1 -1553279044 xNvdtbUxrijunit2
     * -1140430393
     */
    private static String system1 = "VDEVHBvnnL";
    private static String system2 = "AZJhlsszCJ";
    private static String system3 = "xNvdtbUxri";
    private static String peer1 = "junit1";
    private static String peer2 = "junit2";
    private static String peer3 = "junit3";

    private static List<String> peersList = Arrays.asList(peer1, peer2, peer3);

    @Test
    public void firstValueTest() {
        assertEquals(peer1, RendezvousHashing.firstValue(system1, peersList));
        assertEquals(peer2, RendezvousHashing.firstValue(system2, peersList));
        assertEquals(peer3, RendezvousHashing.firstValue(system3, peersList));
    }

    @Test
    public void firstValuesQuantity1Test() {
        List<String> values1 = RendezvousHashing.firstValues(system1, peersList, 1, 1);
        Assert.assertEquals(peer1, values1.get(0));
        Assert.assertEquals(1, values1.size());

        List<String> values2 = RendezvousHashing.firstValues(system2, peersList, 2, 1);
        Assert.assertEquals(peer2, values2.get(0));
        Assert.assertEquals(1, values2.size());

        List<String> values3 = RendezvousHashing.firstValues(system3, peersList, 3, 1);
        Assert.assertEquals(peer3, values3.get(0));
        Assert.assertEquals(1, values3.size());
    }

    @Test
    public void firstValuesQuantity2Test() {
        List<String> values2 = RendezvousHashing.firstValues(system2, peersList, 1, 2);
        Assert.assertEquals(peer2, values2.get(0));
        Assert.assertEquals(1, values2.size());

        List<String> values3 = RendezvousHashing.firstValues(system3, peersList, 2, 2);
        Assert.assertEquals(peer3, values3.get(0));
        Assert.assertEquals(2, values3.size());

        List<String> values1 = RendezvousHashing.firstValues(system1, peersList, 3, 2);
        Assert.assertEquals(peer1, values1.get(0));
        Assert.assertEquals(2, values1.size());
    }

    @Test
    public void firstValuesQuantity3Test() {
        List<String> values3 = RendezvousHashing.firstValues(system3, peersList, 1, 3);
        Assert.assertEquals(peer3, values3.get(0));
        Assert.assertEquals(1, values3.size());

        List<String> values1 = RendezvousHashing.firstValues(system1, peersList, 2, 3);
        Assert.assertEquals(peer1, values1.get(0));
        Assert.assertEquals(2, values1.size());

        List<String> values2 = RendezvousHashing.firstValues(system2, peersList, 3, 3);
        Assert.assertEquals(peer2, values2.get(0));
        Assert.assertEquals(3, values2.size());
    }

    @Test
    public void allValuesTest() {
        List<String> values1 = RendezvousHashing.allValues(system1, peersList, 1);
        Assert.assertEquals(peer1, values1.get(0));
        Assert.assertEquals(1, values1.size());

        List<String> values2 = RendezvousHashing.allValues(system2, peersList, 2);
        Assert.assertEquals(peer2, values2.get(0));
        Assert.assertEquals(2, values2.size());

        List<String> values3 = RendezvousHashing.allValues(system3, peersList, 3);
        Assert.assertEquals(peer3, values3.get(0));
        Assert.assertEquals(3, values3.size());
    }

    @Test
    public void lastValueTest() {
        assertEquals(peer1, RendezvousHashing.lastValue(system1, peersList, 1));
        assertEquals(peer3, RendezvousHashing.lastValue(system2, peersList, 2));
        assertEquals(peer2, RendezvousHashing.lastValue(system3, peersList, 3));
    }
}
