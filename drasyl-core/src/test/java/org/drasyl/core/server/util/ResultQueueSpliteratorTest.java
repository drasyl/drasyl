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

package org.drasyl.core.server.util;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ResultQueueSpliteratorTest {
    static final long timeout = 100;
    ResultQueueSpliterator<String> spliterator;
    static final String testID1 = "testID1";
    static final String testID2 = "testID2";
    static final String testID3 = "testID3";

    @BeforeEach
    public void before() {
        spliterator = new ResultQueueSpliterator<>(timeout);
    }

    @Test
    public void addIntermediateResultTest() {
        assertTrue(spliterator.addIntermediateResult(testID1));
        assertTrue(spliterator.tryAdvance(action -> assertThat(action, is(testID1))));
        assertTrue(spliterator.addIntermediateResult(testID2));
        assertTrue(spliterator.addIntermediateResult(testID3));
        assertTrue(spliterator.tryAdvance(action -> assertThat(action, is(testID2))));
        assertTrue(spliterator.tryAdvance(action -> assertThat(action, is(testID3))));
        assertFalse(spliterator.tryAdvance(null));
    }

    @Test
    public void finishTest() {
        assertTrue(spliterator.addIntermediateResult(testID1));
        assertFalse(spliterator.isFinished());
        spliterator.finish();
        assertTrue(spliterator.isFinished());
    }

    @Test
    public void finishMultiplyTest() {
        spliterator.finish();
        assertFalse(spliterator.tryAdvance(null));
        spliterator.finish();
        assertFalse(spliterator.tryAdvance(null));
    }

    @Test
    public void noAddingAfterFinishTest() {
        assertTrue(spliterator.addIntermediateResult(testID1));
        spliterator.finish();
        assertFalse(spliterator.addIntermediateResult(testID2));
        assertTrue(spliterator.tryAdvance(action -> assertThat(action, is(testID1))));
        assertFalse(spliterator.tryAdvance(null));
    }

    @Test
    public void noAddingAfterTimeoutTest() throws InterruptedException {
        assertTrue(spliterator.addIntermediateResult(testID1));

        Thread.sleep(2 * timeout);// NOSONAR

        assertFalse(spliterator.addIntermediateResult(testID2));
        assertTrue(spliterator.tryAdvance(action -> assertThat(action, is(testID1))));
        assertFalse(spliterator.tryAdvance(null));
    }
}
