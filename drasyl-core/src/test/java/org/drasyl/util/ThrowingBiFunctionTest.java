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
public class ThrowingBiFunctionTest {
    @Nested
    class Apply {
        @Test
        void shouldReturnCorrectValue() throws IOException {
            final ThrowingBiFunction<Integer, Integer, Integer, IOException> function = Integer::sum;

            assertEquals(42, function.apply(40, 2));
        }

        @Test
        void shouldThrowCheckedException(@Mock final IOException e) {
            final ThrowingBiFunction<Integer, Integer, Integer, IOException> function = (i, j) -> {
                throw e;
            };

            assertThrows(IOException.class, () -> function.apply(40, 2));
        }
    }
}
