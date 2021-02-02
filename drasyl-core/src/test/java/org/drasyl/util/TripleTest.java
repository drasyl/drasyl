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

import java.io.IOException;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.drasyl.util.JSONUtil.JACKSON_READER;
import static org.drasyl.util.JSONUtil.JACKSON_WRITER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class TripleTest {
    @Nested
    class First {
        @Test
        void shouldReturnFirstElement() {
            final Triple<Integer, Boolean, String> triple = Triple.of(10, false, "beers");

            assertEquals(10, triple.first());
        }
    }

    @Nested
    class Second {
        @Test
        void shouldReturnSecondElement() {
            final Triple<Integer, Boolean, String> triple = Triple.of(10, false, "beers");

            assertFalse(triple.second());
        }
    }

    @Nested
    class Third {
        @Test
        void shouldReturnThirdElement() {
            final Triple<Integer, Boolean, String> triple = Triple.of(10, false, "beers");

            assertEquals("beers", triple.third());
        }
    }

    @Nested
    class Equals {
        @Test
        void shouldRecognizeEqualTriples() {
            final Triple<Integer, Boolean, String> tripleA = Triple.of(5, false, "beers");
            final Triple<Integer, Boolean, String> tripleB = Triple.of(5, false, "beers");
            final Triple<Integer, Boolean, String> tripleC = Triple.of(null, false, "shots");

            assertEquals(tripleA, tripleA);
            assertEquals(tripleA, tripleB);
            assertEquals(tripleB, tripleA);
            assertNotEquals(null, tripleA);
            assertNotEquals(tripleA, tripleC);
            assertNotEquals(tripleC, tripleA);
        }
    }

    @Nested
    class HashCode {
        @Test
        void shouldRecognizeEqualTriples() {
            final Triple<Integer, Boolean, String> tripleA = Triple.of(5, false, "beers");
            final Triple<Integer, Boolean, String> tripleB = Triple.of(5, false, "beers");
            final Triple<Integer, Boolean, String> tripleC = Triple.of(null, false, "shots");

            assertEquals(tripleA.hashCode(), tripleB.hashCode());
            assertNotEquals(tripleA.hashCode(), tripleC.hashCode());
            assertNotEquals(tripleB.hashCode(), tripleC.hashCode());
        }
    }

    @Nested
    class ToString {
        @Test
        void shouldReturnCorrectString() {
            final String string = Triple.of(5, false, "beers").toString();

            assertEquals("Triple{first=5, second=false, third=beers}", string);
        }
    }

    @Nested
    class JsonDeserialization {
        @Test
        void shouldDeserializeToCorrectObject() throws IOException {
            final String json = "[5, false, \"beers\"]";

            assertEquals(Triple.of(5, false, "beers"), JACKSON_READER.readValue(json, Triple.class));
        }
    }

    @Nested
    class JsonSerialization {
        @Test
        void shouldSerializeToCorrectJson() throws IOException {
            final Triple<Integer, Boolean, String> triple = Triple.of(5, false, "beers");

            assertThatJson(JACKSON_WRITER.writeValueAsString(triple))
                    .isArray()
                    .containsExactlyInAnyOrder(5, false, "beers");
        }
    }
}
