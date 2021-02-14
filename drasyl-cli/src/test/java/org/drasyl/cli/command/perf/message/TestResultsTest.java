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
package org.drasyl.cli.command.perf.message;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class TestResultsTest {
    @Nested
    class IncrementLostMessages {
        @Test
        void shouldThrowExceptionWhenStopped() {
            final TestResults testResults = new TestResults(1, 1, 1, 1, 1, 1, 1);

            assertThrows(IllegalStateException.class, testResults::incrementLostMessages);
        }
    }

    @Nested
    class IncrementOutOfOrderMessages {
        @Test
        void shouldThrowExceptionWhenStopped() {
            final TestResults testResults = new TestResults(1, 1, 1, 1, 1, 1, 1);

            assertThrows(IllegalStateException.class, testResults::incrementOutOfOrderMessages);
        }
    }
}
