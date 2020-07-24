package org.drasyl.peer.connection.message;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.drasyl.util.JSONUtil.JACKSON_READER;
import static org.drasyl.util.JSONUtil.JACKSON_WRITER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(MockitoExtension.class)
class MessageIdTest {
    @Nested
    class Equals {
        @Test
        void shouldRecognizeEqualPairs() {
            MessageId idA = new MessageId("123");
            MessageId idB = new MessageId("123");
            MessageId idC = new MessageId("456");

            assertEquals(idA, idA);
            assertEquals(idA, idB);
            assertEquals(idB, idA);
            assertNotEquals(null, idA);
            assertNotEquals(idA, idC);
            assertNotEquals(idC, idA);
        }
    }

    @Nested
    class HashCode {
        @Test
        void shouldRecognizeEqualPairs() {
            MessageId idA = new MessageId("123");
            MessageId idB = new MessageId("123");
            MessageId idC = new MessageId("456");

            assertEquals(idA.hashCode(), idB.hashCode());
            assertNotEquals(idA.hashCode(), idC.hashCode());
            assertNotEquals(idB.hashCode(), idC.hashCode());
        }
    }

    @Nested
    class ToString {
        @Test
        void shouldReturnCorrectString() {
            String string = new MessageId("123").toString();

            assertEquals("123", string);
        }
    }

    @Nested
    class JsonDeserialization {
        @Test
        void shouldDeserializeToCorrectObject() throws IOException {
            String json = "\"123\"";

            assertEquals(
                    new MessageId("123"),
                    JACKSON_READER.readValue(json, MessageId.class)
            );
        }
    }

    @Nested
    class JsonSerialization {
        @Test
        void shouldSerializeToCorrectJson() throws IOException {
            MessageId id = new MessageId("123");

            assertThatJson(JACKSON_WRITER.writeValueAsString(id))
                    .isEqualTo("\"123\"");
        }
    }

    @Nested
    class RandomMessageId {
        @Test
        void shouldReturnRandomMessageId() {
            MessageId idA = MessageId.randomMessageId();
            MessageId idB = MessageId.randomMessageId();

            assertNotNull(idA);
            assertNotNull(idB);
            assertNotEquals(idA, idB);
        }
    }
}