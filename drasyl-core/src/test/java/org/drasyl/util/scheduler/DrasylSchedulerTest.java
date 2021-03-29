/*
 * Copyright (c) 2020-2021 Heiko Bornholdt and Kevin Röbert
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
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

            verify(scheduler).now(unit);
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

            verify(scheduler).scheduleDirect(run);
        }

        @Test
        void scheduleDirect2() {
            final Runnable run = () -> {
            };
            final long delay = 1;
            final TimeUnit unit = TimeUnit.SECONDS;

            wrapper.scheduleDirect(run, delay, unit);

            verify(scheduler).scheduleDirect(run, delay, unit);
        }

        @Test
        void schedulePeriodicallyDirect() {
            final Runnable run = () -> {
            };
            final long delay = 1;
            final TimeUnit unit = TimeUnit.SECONDS;

            wrapper.schedulePeriodicallyDirect(run, delay, delay, unit);

            verify(scheduler).schedulePeriodicallyDirect(run, delay, delay, unit);
        }

        @SuppressWarnings({ "rawtypes", "unchecked" })
        @Test
        void when(@Mock final Function function) {
            wrapper.when(function);

            verify(scheduler).when(function);
        }
    }
}
