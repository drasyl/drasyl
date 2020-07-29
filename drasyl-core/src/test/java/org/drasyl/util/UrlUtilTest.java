package org.drasyl.util;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.MalformedURLException;
import java.net.URL;

import static org.drasyl.util.UrlUtil.createUrl;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class UrlUtilTest {
    @Nested
    class CreateUrl {
        @Test
        void shouldReturnUrlForValidString() throws MalformedURLException {
            assertEquals(new URL("https://example.com"), createUrl("https://example.com"));
        }

        @Test
        void shouldThrowNullPointerExceptionForNullString() {
            assertThrows(NullPointerException.class, () -> createUrl(null));
        }

        @Test
        void shouldThrowIllegalArgumentExceptionForInvalidString() {
            assertThrows(IllegalArgumentException.class, () -> createUrl("foo.bar"));
        }
    }
}