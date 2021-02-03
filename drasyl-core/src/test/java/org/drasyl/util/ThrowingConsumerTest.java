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
package org.drasyl.util;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class ThrowingConsumerTest {
    @Nested
    class Accept {
        @Test
        void shouldConsume() throws IOException {
            final Integer[] input = { 0 };
            final ThrowingConsumer<Integer, IOException> consumer = i -> input[0] = i;

            consumer.accept(1337);

            assertEquals(1337, input[0]);
        }

        @Test
        void shouldThrowCheckedException(@Mock final IOException e) {
            final ThrowingConsumer<Integer, IOException> consumer = i -> {
                throw e;
            };

            assertThrows(IOException.class, () -> consumer.accept(1337));
        }
    }
}
