package org.drasyl.core.common.message;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.javacrumbs.jsonunit.core.Option;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class ConnectionExceptionMessageTest {
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    @Test
    public void toJson() throws JsonProcessingException {
        ConnectionExceptionMessage message = new ConnectionExceptionMessage("something horrible has happened");

        assertThatJson(JSON_MAPPER.writeValueAsString(message))
                .when(Option.IGNORING_ARRAY_ORDER)
                .isEqualTo("{\"@type\":\"" + message.getClass().getSimpleName() + "\",\"id\":\"" + message.getId() + "\",\"exception\":\"something horrible has happened\"}");

        // Ignore toString()
        message.toString();
    }

    @Test
    public void fromJson() throws IOException {
        String json = "{\"@type\":\"" + ConnectionExceptionMessage.class.getSimpleName() + "\",\"id\":\"77175D7235920F3BA17341D7\"," +
                "\"exception\":\"something horrible has happened\"}";

        assertThat(JSON_MAPPER.readValue(json, Message.class), instanceOf(ConnectionExceptionMessage.class));
    }

    @Test
    public void nullTest() {
        Assertions.assertThrows(NullPointerException.class, () -> new ConnectionExceptionMessage((String) null), "NodeServerException requires an exception");

        Assertions.assertThrows(NullPointerException.class, () -> new ConnectionExceptionMessage((Exception) null), "NodeServerException requires an exception");
    }

    @Test
    void testEquals() {
        ConnectionExceptionMessage message1 = new ConnectionExceptionMessage("something horrible has happened");
        ConnectionExceptionMessage message2 = new ConnectionExceptionMessage("something horrible has happened");
        ConnectionExceptionMessage message3 = new ConnectionExceptionMessage("something dreadful has happened");

        assertEquals(message1, message2);
        assertNotEquals(message2, message3);
    }

    @Test
    void testHashCode() {
        MessageExceptionMessage message1 = new MessageExceptionMessage("something horrible has happened");
        MessageExceptionMessage message2 = new MessageExceptionMessage("something horrible has happened");
        MessageExceptionMessage message3 = new MessageExceptionMessage("something dreadful has happened");

        assertEquals(message1.hashCode(), message2.hashCode());
        assertNotEquals(message2.hashCode(), message3.hashCode());
    }
}
