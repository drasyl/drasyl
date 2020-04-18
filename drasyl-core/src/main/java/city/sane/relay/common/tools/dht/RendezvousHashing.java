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

package city.sane.relay.common.tools.dht;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeMap;

import org.apache.commons.codec.digest.MurmurHash3;

/**
 * A map that generates a list of responsible peers via the rendezvous hashing
 * technique. Deterministically assigns a fixed number of peers to a given
 * object hash.
 */
public final class RendezvousHashing {
    private RendezvousHashing() {
    }

    /**
     * Does the actual hashing over the peer list and a given input hash.
     * 
     * @param <E>        type must return a unique identifier upon call of
     *                   {@link E#toString()}.
     * @param objectHash the hash that is used to compute the responsible peers
     * @param peers      a reference to a collection containing all peers. Type E
     *                   must return a unique identifier upon call of
     *                   {@link E#toString()}.
     * @param redundancy the (maximum) number of peers an object should be assigned
     *                   to
     * @return the list of values that represent the most responsible peers
     */
    private static <E> TreeMap<Integer, E> doHashing(String objectHash, Collection<E> peers, int redundancy) {
        TreeMap<Integer, E> elements = new TreeMap<>();

        for (E peer : peers) {
            elements.put(MurmurHash3.hash32(objectHash + peer.toString()), peer);
            if (elements.size() > redundancy) {
                elements.remove(elements.lastKey());
            }
        }

        return elements;
    }

    /**
     * Returns the first (lowest) computed value. It can be seen as the 'most
     * responsible' peer.
     * 
     * @param <E>        type must return a unique identifier upon call of
     *                   {@link E#toString()}.
     * @param objectHash the hash that is used to compute the responsible peers
     * @param peers      a reference to a collection containing all peers. Type E
     *                   must return a unique identifier upon call of
     *                   {@link E#toString()}.
     * @param redundancy the (maximum) number of peers an object should be assigned
     *                   to
     * @return
     * @return the value that represents the most responsible peer or null if the
     *         peer collection is empty
     */
    public static <E> E firstValue(String objectHash, Collection<E> peers) {
        return doHashing(objectHash, peers, 1).firstEntry().getValue();
    }

    /**
     * Returns a list of the first (lowest) n computed values. They can be seen as
     * the 'most responsible' peers of the peers that are responsible at all.
     * 
     * @param <E>        type must return a unique identifier upon call of
     *                   {@link E#toString()}.
     * @param objectHash the hash that is used to compute the responsible peers
     * @param n          the (maximum) number of values to be returned
     * @param peers      a reference to a collection containing all peers. Type E
     *                   must return a unique identifier upon call of
     *                   {@link E#toString()}.
     * @param redundancy the (maximum) number of peers an object should be assigned
     *                   to
     * @return the list of values that represent the most responsible peers
     */
    public static <E> List<E> firstValues(String objectHash, Collection<E> peers, int redundancy, int n) {
        ArrayList<E> values = new ArrayList<>();
        TreeMap<Integer, E> elements = doHashing(objectHash, peers, redundancy);

        for (int resultHash : elements.navigableKeySet()) {
            if (n <= 0)
                break;
            values.add(elements.get(resultHash));
            n--;
        }

        return values;
    }

    /**
     * Returns a list of all peers currently in the collection that are responsible
     * at all, sorted from 'most responsible' to 'least responsible'.
     * 
     * @param <E>        type must return a unique identifier upon call of
     *                   {@link E#toString()}.
     * @param objectHash the hash that is used to compute the responsible peers
     * @param peers      a reference to a collection containing all peers. Type E
     *                   must return a unique identifier upon call of
     *                   {@link E#toString()}.
     * @param redundancy the (maximum) number of peers an object should be assigned
     *                   to
     * @return the list of values that represent the responsible peers
     */
    public static <E> List<E> allValues(String objectHash, Collection<E> peers, int redundancy) {
        ArrayList<E> values = new ArrayList<>();
        TreeMap<Integer, E> elements = doHashing(objectHash, peers, redundancy);

        for (int resultHash : elements.navigableKeySet()) {
            values.add(elements.get(resultHash));
        }

        return values;
    }

    /**
     * Returns the last (highest) computed value. It can be seen as the 'least
     * responsible' peer of the peers that are responsible at all.
     * 
     * @param <E>        type must return a unique identifier upon call of
     *                   {@link E#toString()}.
     * @param objectHash the hash that is used to compute the responsible peers
     * @param peers      a reference to a collection containing all peers. Type E
     *                   must return a unique identifier upon call of
     *                   {@link E#toString()}.
     * @param redundancy the (maximum) number of peers an object should be assigned
     *                   to
     * @return the value that represents the least responsible peer or null if the
     *         peer collection is empty
     */
    public static <E> E lastValue(String objectHash, Collection<E> peers, int redundancy) {
        return doHashing(objectHash, peers, redundancy).lastEntry().getValue();
    }
}
