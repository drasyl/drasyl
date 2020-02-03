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

package org.drasyl.all.session.util;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class AutoDeletionBucketTest {
    static final int longInterval = 10000;
    static final int shortInterval = 500;
    static final String testID1 = "testID1";
    static final String testID2 = "testID2";
    static final String testID3 = "testID3";
    static final String testID4 = "testID4";

    @Test
    public void argumentTest() {
        assertThrows(IllegalArgumentException.class, () -> new AutoDeletionBucket<String>(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> new AutoDeletionBucket<String>(0, 0));
        assertThrows(IllegalArgumentException.class, () -> new AutoDeletionBucket<String>(Long.MIN_VALUE, 1));
        assertThrows(IllegalArgumentException.class, () -> new AutoDeletionBucket<String>(0, Long.MIN_VALUE));
        new AutoDeletionBucket<String>(0, 1).cancelTimer();
    }

    @SuppressWarnings("null")
    @Test
    public void requireNonNullTest() {
        assertThrows(NullPointerException.class, () -> new AutoDeletionBucket<String>((Long) null, 1));
        assertThrows(NullPointerException.class, () -> new AutoDeletionBucket<String>(0, (Long) null));

        AutoDeletionBucket<String> bucket = new AutoDeletionBucket<>(longInterval, longInterval);

        bucket.addAll(List.of(testID1, testID2));
        assertThrows(NullPointerException.class, () -> bucket.add(null));
        assertThrows(NullPointerException.class, () -> bucket.addAll(null));
        assertThrows(NullPointerException.class, () -> bucket.contains(null));

        ArrayList<String> list = new ArrayList<>(List.of(testID2, testID3, testID4));
        list.add(null);
        bucket.addAll(list);
        assertThat(bucket.getElements(), containsInAnyOrder(testID1, testID2, testID3, testID4));
        assertEquals(4, bucket.getElements().size());

        bucket.cancelTimer();
    }

    @Test
    public void addTest() throws InterruptedException {
        AutoDeletionBucket<String> bucket = new AutoDeletionBucket<>(longInterval, longInterval);

        assertTrue(bucket.add(testID1));
        assertTrue(bucket.add(testID2));
        assertFalse(bucket.add(testID1));
        assertFalse(bucket.add(testID2));
        assertTrue(bucket.add(testID3));
        assertFalse(bucket.add(testID1));
        assertFalse(bucket.add(testID2));
        assertThat(bucket.getElements(), containsInAnyOrder(testID1, testID2, testID3));
        assertEquals(3, bucket.getElements().size());

        bucket.cancelTimer();
    }

    @Test
    public void addAllTest() throws InterruptedException {
        AutoDeletionBucket<String> bucket = new AutoDeletionBucket<>(longInterval, longInterval);

        bucket.addAll(List.of(testID1, testID2, testID1));
        bucket.addAll(Set.of(testID2, testID3));
        assertThat(bucket.getElements(), containsInAnyOrder(testID1, testID2, testID3));
        assertEquals(3, bucket.getElements().size());

        bucket.cancelTimer();
    }

    @Test
    public void autoDeletionTest() throws InterruptedException {
        AutoDeletionBucket<String> bucket = new AutoDeletionBucket<>(shortInterval, shortInterval);

        bucket.addAll(List.of(testID1, testID2, testID3));
        Thread.sleep(3 * shortInterval);// NOSONAR
        assertTrue(bucket.getElements().isEmpty());

        bucket.cancelTimer();
    }

    @Test
    public void instantDeletionTest() throws InterruptedException {
        AutoDeletionBucket<String> bucket = new AutoDeletionBucket<>(0, 1);

        bucket.addAll(List.of(testID1, testID2, testID3));
        Thread.sleep(500);// NOSONAR
        assertTrue(bucket.getElements().isEmpty());

        bucket.cancelTimer();
    }

    @Test
    public void containsTest() {
        AutoDeletionBucket<String> bucket = new AutoDeletionBucket<>(longInterval, longInterval);

        bucket.add(testID1);
        assertTrue(bucket.contains(testID1));
        bucket.addAll(List.of(testID2, testID3));
        assertTrue(bucket.contains(testID2));
        assertTrue(bucket.contains(testID3));
        assertFalse(bucket.contains(testID4));

        bucket.cancelTimer();
    }

    @Test
    public void cancelTimerTest() throws InterruptedException {
        AutoDeletionBucket<String> bucket = new AutoDeletionBucket<>(shortInterval, shortInterval);

        bucket.addAll(List.of(testID1, testID2, testID3));
        assertThat(bucket.getElements(), containsInAnyOrder(testID1, testID2, testID3));
        assertEquals(3, bucket.getElements().size());
        bucket.cancelTimer();
        Thread.sleep(shortInterval);// NOSONAR
        assertThat(bucket.getElements(), containsInAnyOrder(testID1, testID2, testID3));
    }
}
