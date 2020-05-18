package org.drasyl.peer.connection.message;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.javacrumbs.jsonunit.core.Option;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.drasyl.peer.connection.message.ConnectionExceptionMessage.Error.CONNECTION_ERROR_HANDSHAKE;
import static org.drasyl.peer.connection.message.ConnectionExceptionMessage.Error.CONNECTION_ERROR_PING_PONG;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ConnectionExceptionMessageTest {
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private String correspondingId;

    @BeforeEach
    void setUp() {
        correspondingId = "correspondingId";
    }

    @Test
    void toJson() throws JsonProcessingException {
        ConnectionExceptionMessage message = new ConnectionExceptionMessage(CONNECTION_ERROR_PING_PONG);

        assertThatJson(JSON_MAPPER.writeValueAsString(message))
                .when(Option.IGNORING_ARRAY_ORDER)
                .isEqualTo("{\"@type\":\"" + message.getClass().getSimpleName() + "\",\"id\":\"" + message.getId() + "\",\"error\":\"Too many Ping Messages were not answered with a Pong Message.\"}");

        // Ignore toString()
        message.toString();
    }

    @Test
    void fromJson() throws IOException {
        String json = "{\"@type\":\"" + ConnectionExceptionMessage.class.getSimpleName() + "\",\"id\":\"77175D7235920F3BA17341D7\"," +
                "\"error\":\"Too many PingMessages were not answered with a PongMessage.\"}";

        assertThat(JSON_MAPPER.readValue(json, Message.class), instanceOf(ConnectionExceptionMessage.class));
    }

    @Test
    void nullTest() {
        assertThrows(NullPointerException.class, () -> new ConnectionExceptionMessage(null), "ConnectionExceptionMessage requires an error type");
    }

    @Test
    void testEquals() {
        ConnectionExceptionMessage message1 = new ConnectionExceptionMessage(CONNECTION_ERROR_PING_PONG);
        ConnectionExceptionMessage message2 = new ConnectionExceptionMessage(CONNECTION_ERROR_PING_PONG);
        ConnectionExceptionMessage message3 = new ConnectionExceptionMessage(CONNECTION_ERROR_HANDSHAKE);

        assertEquals(message1, message2);
        assertNotEquals(message2, message3);
    }

    @Test
    void testHashCode() {
        ConnectionExceptionMessage message1 = new ConnectionExceptionMessage(CONNECTION_ERROR_PING_PONG);
        ConnectionExceptionMessage message2 = new ConnectionExceptionMessage(CONNECTION_ERROR_PING_PONG);
        ConnectionExceptionMessage message3 = new ConnectionExceptionMessage(CONNECTION_ERROR_HANDSHAKE);

        assertEquals(message1.hashCode(), message2.hashCode());
        assertNotEquals(message2.hashCode(), message3.hashCode());
    }
}
