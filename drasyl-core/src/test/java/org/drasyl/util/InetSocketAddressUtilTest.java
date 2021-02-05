package org.drasyl.util;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetSocketAddress;

import static org.drasyl.util.InetSocketAddressUtil.socketAddressFromString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
public class InetSocketAddressUtilTest {
    @Nested
    class SocketAddressFromString {
        @Test
        void shouldReturnCorrectSocketAddress() {
            assertEquals(new InetSocketAddress("example.com", 22527), socketAddressFromString("example.com:22527"));
        }

        @SuppressWarnings("ConstantConditions")
        @Test
        void shouldThrowNullPointerExceptionForNullString() {
            assertThrows(NullPointerException.class, () -> socketAddressFromString(null));
        }

        @Test
        void shouldThrowIllegalArgumentExceptionForStringWithoutHostname() {
            assertThrows(IllegalArgumentException.class, () -> socketAddressFromString("123"));
        }

        @Test
        void shouldThrowIllegalArgumentExceptionForStringWithoutPort() {
            assertThrows(IllegalArgumentException.class, () -> socketAddressFromString("example.com"));
        }

        @Test
        void shouldThrowIllegalArgumentExceptionForStringWithInvalidPort() {
            assertThrows(IllegalArgumentException.class, () -> socketAddressFromString("example.com:999999"));
        }

        @Test
        void shouldThrowIllegalArgumentExceptionForStringWithInvalidPortFormat() {
            assertThrows(IllegalArgumentException.class, () -> socketAddressFromString("example.com:baz"));
        }
    }
}
