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
package org.drasyl.util;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
@Disabled("This tests runs only when started in own JVM")
class DrasylSchedulerTest {
    @Test
    void shouldReturnImmediatelyWhenSchedulerIsNotInstantiated() {
        assertFalse(DrasylScheduler.lightSchedulerCreated);
        assertFalse(DrasylScheduler.heavySchedulerCreated);
    }

    @Test
    void shouldReturnImmediatelyWhenSchedulerIsNotInstantiatedAndShutdownIsCalled() {
        DrasylScheduler.shutdown().join();

        assertFalse(DrasylScheduler.lightSchedulerCreated);
        assertFalse(DrasylScheduler.heavySchedulerCreated);
    }

    @Test
    void shouldNotReturnImmediatelyWhenLightSchedulerIsInstantiated() {
        assertNotNull(DrasylScheduler.getInstanceLight());

        assertTrue(DrasylScheduler.lightSchedulerCreated);
        assertFalse(DrasylScheduler.heavySchedulerCreated);

        // shutdown
        DrasylScheduler.shutdown().join();
    }

    @Test
    void shouldNotReturnImmediatelyWhenHeavySchedulerIsInstantiated() {
        assertNotNull(DrasylScheduler.getInstanceHeavy());

        assertFalse(DrasylScheduler.lightSchedulerCreated);
        assertTrue(DrasylScheduler.heavySchedulerCreated);

        // shutdown
        DrasylScheduler.shutdown().join();
    }

    @Test
    void shouldNotReturnImmediatelyWhenSchedulerIsInstantiated() {
        assertNotNull(DrasylScheduler.getInstanceLight());
        assertNotNull(DrasylScheduler.getInstanceHeavy());

        assertTrue(DrasylScheduler.lightSchedulerCreated);
        assertTrue(DrasylScheduler.heavySchedulerCreated);

        // shutdown
        DrasylScheduler.shutdown().join();
    }
}