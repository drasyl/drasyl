package org.drasyl.peer.connection.message;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.drasyl.peer.connection.message.MessageId.isValidMessageId;
import static org.drasyl.peer.connection.message.MessageId.randomMessageId;
import static org.drasyl.util.JSONUtil.JACKSON_READER;
import static org.drasyl.util.JSONUtil.JACKSON_WRITER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class MessageIdTest {
    @Nested
    class Constructor {
        @Test
        void shouldValidateGivenId() {
            assertThrows(IllegalArgumentException.class, () -> new MessageId("xyz"));
        }
    }

    @Nested
    class Equals {
        @Test
        void shouldRecognizeEqualPairs() {
            System.out.println();
            final MessageId idA = new MessageId("412176952b5b81fd13f84a7c");
            final MessageId idB = new MessageId("412176952b5b81fd13f84a7c");
            final MessageId idC = new MessageId("78c36c82b8d11c7217a011b3");

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
            final MessageId idA = new MessageId("412176952b5b81fd13f84a7c");
            final MessageId idB = new MessageId("412176952b5b81fd13f84a7c");
            final MessageId idC = new MessageId("78c36c82b8d11c7217a011b3");

            assertEquals(idA.hashCode(), idB.hashCode());
            assertNotEquals(idA.hashCode(), idC.hashCode());
            assertNotEquals(idB.hashCode(), idC.hashCode());
        }
    }

    @Nested
    class ToString {
        @Test
        void shouldReturnCorrectString() {
            final String string = new MessageId("412176952b5b81fd13f84a7c").toString();

            assertEquals("412176952b5b81fd13f84a7c", string);
        }
    }

    @Nested
    class JsonDeserialization {
        @Test
        void shouldDeserializeToCorrectObject() throws IOException {
            final String json = "\"412176952b5b81fd13f84a7c\"";

            assertEquals(
                    new MessageId("412176952b5b81fd13f84a7c"),
                    JACKSON_READER.readValue(json, MessageId.class)
            );
        }
    }

    @Nested
    class JsonSerialization {
        @Test
        void shouldSerializeToCorrectJson() throws IOException {
            final MessageId id = new MessageId("412176952b5b81fd13f84a7c");

            assertThatJson(JACKSON_WRITER.writeValueAsString(id))
                    .isEqualTo("\"412176952b5b81fd13f84a7c\"");
        }
    }

    @Nested
    class RandomMessageId {
        @Test
        void shouldReturnRandomMessageId() {
            final MessageId idA = randomMessageId();
            final MessageId idB = randomMessageId();

            assertNotNull(idA);
            assertNotNull(idB);
            assertNotEquals(idA, idB);
        }
    }

    @Nested
    class IsValidMessageId {
        @Test
        void shouldReturnFalseForNullString() {
            assertFalse(isValidMessageId(null));
        }

        @Test
        void shouldReturnFalseForStringWithWrongLength() {
            assertFalse(isValidMessageId("abc"));
        }

        @Test
        void shouldReturnFalseForStringWithWrongChar() {
            assertFalse(isValidMessageId("xyz"));
        }

        @Test
        void shouldReturnTrueForValidString() {
            assertTrue(isValidMessageId("f3d0aee7962de47a849bd7b0"));
        }
    }
}