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

package city.sane.relay.server.session.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import city.sane.relay.server.session.util.MessageBucket;

public class MessageBucketTest {
    static final int size = 3;
    MessageBucket bucket;
    static final String testID1 = "testID1";
    static final String testID2 = "testID2";
    static final String testID3 = "testID3";
    static final String testID4 = "testID4";

    @BeforeEach
    public void before() {
        bucket = new MessageBucket(size);
    }

    @Test
    public void argumentTest() {
        assertThrows(IllegalArgumentException.class, () -> new MessageBucket(0));
        assertThrows(IllegalArgumentException.class, () -> new MessageBucket(-1));
        assertThrows(IllegalArgumentException.class, () -> new MessageBucket(Integer.MIN_VALUE));
    }

    @SuppressWarnings("null")
    @Test
    public void requireNonNullTest() {
        assertThrows(NullPointerException.class, () -> bucket.add(null));
        assertThrows(NullPointerException.class, () -> bucket.contains(null));
        assertThrows(NullPointerException.class, () -> new MessageBucket((Integer) null));
    }

    @Test
    public void addMessageTest() {
        assertTrue(bucket.add(testID1));
        assertTrue(bucket.add(testID2));
        assertFalse(bucket.add(testID1));
        assertFalse(bucket.add(testID2));
        assertTrue(bucket.add(testID3));
        assertFalse(bucket.add(testID1));
        assertFalse(bucket.add(testID2));

        assertEquals(Arrays.asList(testID3, testID1, testID2), bucket.getMessages());
        assertEquals(3, bucket.getMessages().size());
    }

    @Test
    public void containsTest() {
        bucket.add(testID1);
        bucket.add(testID2);
        assertTrue(bucket.contains(testID1));
        assertTrue(bucket.contains(testID2));
        bucket.add(testID3);
        bucket.add(testID4);
        assertTrue(bucket.contains(testID3));
        assertTrue(bucket.contains(testID4));
        assertFalse(bucket.contains(testID1));
    }

    @Test
    public void isAtFullCapacityTest() {
        bucket.add(testID1);
        assertFalse(bucket.isAtFullCapacity());
        bucket.add(testID2);
        assertFalse(bucket.isAtFullCapacity());
        bucket.add(testID3);
        assertTrue(bucket.isAtFullCapacity());
        bucket.add(testID4);
        assertTrue(bucket.isAtFullCapacity());
    }
}
