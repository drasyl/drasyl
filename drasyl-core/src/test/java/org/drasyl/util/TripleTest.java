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
            Triple<Integer, Boolean, String> triple = Triple.of(10, false, "beers");

            assertEquals(10, triple.first());
        }
    }

    @Nested
    class Second {
        @Test
        void shouldReturnSecondElement() {
            Triple<Integer, Boolean, String> triple = Triple.of(10, false, "beers");

            assertFalse(triple.second());
        }
    }

    @Nested
    class Third {
        @Test
        void shouldReturnThirdElement() {
            Triple<Integer, Boolean, String> triple = Triple.of(10, false, "beers");

            assertEquals("beers", triple.third());
        }
    }

    @Nested
    class Equals {
        @Test
        void shouldRecognizeEqualTriples() {
            Triple<Integer, Boolean, String> tripleA = Triple.of(5, false, "beers");
            Triple<Integer, Boolean, String> tripleB = Triple.of(5, false, "beers");
            Triple<Integer, Boolean, String> tripleC = Triple.of(null, false, "shots");

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
            Triple<Integer, Boolean, String> tripleA = Triple.of(5, false, "beers");
            Triple<Integer, Boolean, String> tripleB = Triple.of(5, false, "beers");
            Triple<Integer, Boolean, String> tripleC = Triple.of(null, false, "shots");

            assertEquals(tripleA.hashCode(), tripleB.hashCode());
            assertNotEquals(tripleA.hashCode(), tripleC.hashCode());
            assertNotEquals(tripleB.hashCode(), tripleC.hashCode());
        }
    }

    @Nested
    class ToString {
        @Test
        void shouldReturnCorrectString() {
            String string = Triple.of(5, false, "beers").toString();

            assertEquals("Triple{first=5, second=false, third=beers}", string);
        }
    }

    @Nested
    class JsonDeserialization {
        @Test
        void shouldDeserializeToCorrectObject() throws IOException {
            String json = "[5, false, \"beers\"]";

            assertEquals(Triple.of(5, false, "beers"), JACKSON_READER.readValue(json, Triple.class));
        }
    }

    @Nested
    class JsonSerialization {
        @Test
        void shouldSerializeToCorrectJson() throws IOException {
            Triple triple = Triple.of(5, false, "beers");

            assertThatJson(JACKSON_WRITER.writeValueAsString(triple))
                    .isArray()
                    .containsExactlyInAnyOrder(5, false, "beers");
        }
    }
}