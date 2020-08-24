package org.drasyl.util;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.drasyl.util.LoggingUtil.sanitizeLogArg;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@ExtendWith(MockitoExtension.class)
class LoggingUtilTest {
    @Nested
    class SanitizeLogArg {
        @Test
        void shouldHandleNullValues() {
            assertNull(sanitizeLogArg(null));
        }

        @Test
        void shouldReplaceNewlines() {
            assertEquals("Foo\\nBar", sanitizeLogArg("Foo\nBar"));
            assertEquals("Foo\\r\\nBar", sanitizeLogArg("Foo\\r\\nBar"));
        }
    }
}