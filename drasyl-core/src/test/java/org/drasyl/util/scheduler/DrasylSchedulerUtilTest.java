/*
 * Copyright (c) 2020-2021.
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
package org.drasyl.util.scheduler;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
@Disabled("This tests runs only when started in own JVM")
class DrasylSchedulerUtilTest {
    @Test
    void shouldReturnImmediatelyWhenSchedulerIsNotInstantiated() {
        assertFalse(DrasylSchedulerUtil.lightSchedulerCreated);
        assertFalse(DrasylSchedulerUtil.heavySchedulerCreated);
    }

    @Test
    void shouldReturnImmediatelyWhenSchedulerIsNotInstantiatedAndShutdownIsCalled() {
        DrasylSchedulerUtil.shutdown().join();

        assertFalse(DrasylSchedulerUtil.lightSchedulerCreated);
        assertFalse(DrasylSchedulerUtil.heavySchedulerCreated);
    }

    @Test
    void shouldNotReturnImmediatelyWhenLightSchedulerIsInstantiated() {
        assertNotNull(DrasylSchedulerUtil.getInstanceLight());

        assertTrue(DrasylSchedulerUtil.lightSchedulerCreated);
        assertFalse(DrasylSchedulerUtil.heavySchedulerCreated);

        // shutdown
        DrasylSchedulerUtil.shutdown().join();
    }

    @Test
    void shouldNotReturnImmediatelyWhenHeavySchedulerIsInstantiated() {
        assertNotNull(DrasylSchedulerUtil.getInstanceHeavy());

        assertFalse(DrasylSchedulerUtil.lightSchedulerCreated);
        assertTrue(DrasylSchedulerUtil.heavySchedulerCreated);

        // shutdown
        DrasylSchedulerUtil.shutdown().join();
    }

    @Test
    void shouldNotReturnImmediatelyWhenSchedulerIsInstantiated() {
        assertNotNull(DrasylSchedulerUtil.getInstanceLight());
        assertNotNull(DrasylSchedulerUtil.getInstanceHeavy());

        assertTrue(DrasylSchedulerUtil.lightSchedulerCreated);
        assertTrue(DrasylSchedulerUtil.heavySchedulerCreated);

        // shutdown
        DrasylSchedulerUtil.shutdown().join();
    }
}
