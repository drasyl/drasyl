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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.drasyl.util.JSONUtil.JACKSON_READER;
import static org.drasyl.util.JSONUtil.JACKSON_WRITER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PairTest {
    @Nested
    class First {
        @Test
        void shouldReturnFirstElement() {
            final Pair<Integer, String> pair = Pair.of(10, "beers");

            assertEquals(10, pair.first());
        }
    }

    @Nested
    class Second {
        @Test
        void shouldReturnSecondElement() {
            final Pair<Integer, String> pair = Pair.of(10, "beers");

            assertEquals("beers", pair.second());
        }
    }

    @Nested
    class Equals {
        @Test
        void shouldRecognizeEqualPairs() {
            final Pair<Integer, String> pairA = Pair.of(5, "beers");
            final Pair<Integer, String> pairB = Pair.of(5, "beers");
            final Pair<Integer, String> pairC = Pair.of(null, "shots");

            assertEquals(pairA, pairA);
            assertEquals(pairA, pairB);
            assertEquals(pairB, pairA);
            assertNotEquals(null, pairA);
            assertNotEquals(pairA, pairC);
            assertNotEquals(pairC, pairA);
        }
    }

    @Nested
    class HashCode {
        @Test
        void shouldRecognizeEqualPairs() {
            final Pair<Integer, String> pairA = Pair.of(5, "beers");
            final Pair<Integer, String> pairB = Pair.of(5, "beers");
            final Pair<Integer, String> pairC = Pair.of(10, "shots");

            assertEquals(pairA.hashCode(), pairB.hashCode());
            assertNotEquals(pairA.hashCode(), pairC.hashCode());
            assertNotEquals(pairB.hashCode(), pairC.hashCode());
        }
    }

    @Nested
    class ToString {
        @Test
        void shouldReturnCorrectString() {
            final String string = Pair.of(5, "beers").toString();

            assertEquals("Pair{first=5, second=beers}", string);
        }
    }

    @Nested
    class JsonDeserialization {
        @Test
        void shouldDeserializeToCorrectObject() throws IOException {
            final String json = "[5, \"beers\"]";

            assertEquals(Pair.of(5, "beers"), JACKSON_READER.readValue(json, Pair.class));
        }
    }

    @Nested
    class JsonSerialization {
        @Test
        void shouldSerializeToCorrectJson() throws IOException {
            final Pair<Integer, String> pair = Pair.of(5, "beers");

            assertThatJson(JACKSON_WRITER.writeValueAsString(pair))
                    .isArray()
                    .containsExactlyInAnyOrder(5, "beers");
        }
    }
}