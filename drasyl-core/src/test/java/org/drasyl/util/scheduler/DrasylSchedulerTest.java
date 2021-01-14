/*
 * Copyright (c) 2021.
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

import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.functions.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DrasylSchedulerTest {
    @Nested
    class Wrapper {
        @Mock
        Scheduler scheduler;
        DrasylScheduler wrapper;

        @BeforeEach
        void setUp() {
            wrapper = DrasylScheduler.wrap(scheduler, "test");
        }

        @Test
        void createWorker() {
            wrapper.createWorker();

            verify(scheduler).createWorker();
        }

        @Test
        void now() {
            final TimeUnit unit = TimeUnit.SECONDS;
            wrapper.now(unit);

            verify(scheduler).now(eq(unit));
        }

        @Test
        void start() {
            wrapper.start();

            verify(scheduler).start();
        }

        @Test
        void shutdown() {
            wrapper.shutdown();

            verify(scheduler).shutdown();
        }

        @Test
        void scheduleDirect() {
            final Runnable run = () -> {
            };
            wrapper.scheduleDirect(run);

            verify(scheduler).scheduleDirect(eq(run));
        }

        @Test
        void scheduleDirect2() {
            final Runnable run = () -> {
            };
            final long delay = 1;
            final TimeUnit unit = TimeUnit.SECONDS;

            wrapper.scheduleDirect(run, delay, unit);

            verify(scheduler).scheduleDirect(eq(run), eq(delay), eq(unit));
        }

        @Test
        void schedulePeriodicallyDirect() {
            final Runnable run = () -> {
            };
            final long delay = 1;
            final TimeUnit unit = TimeUnit.SECONDS;

            wrapper.schedulePeriodicallyDirect(run, delay, delay, unit);

            verify(scheduler).schedulePeriodicallyDirect(eq(run), eq(delay), eq(delay), eq(unit));
        }

        @Test
        void when() {
            final Function function = mock(Function.class);
            wrapper.when(function);

            verify(scheduler).when(eq(function));
        }
    }
}