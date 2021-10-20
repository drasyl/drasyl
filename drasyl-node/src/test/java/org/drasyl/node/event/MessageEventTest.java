/*
 * Copyright (c) 2020-2021 Heiko Bornholdt and Kevin Röbert
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.drasyl.node.event;

import org.drasyl.identity.IdentityPublicKey;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(MockitoExtension.class)
class MessageEventTest {
    @Mock
    private IdentityPublicKey sender;
    @Mock
    private Object message;

    @Nested
    class GetMessage {
        @Test
        void shouldReturnMessage() {
            final MessageEvent event = MessageEvent.of(sender, message);

            assertEquals(message, event.getPayload());
        }
    }

    @Nested
    class Equals {
        @Mock
        private Object message2;

        @Test
        void notSameBecauseOfDifferentMessage() {
            final MessageEvent event1 = MessageEvent.of(sender, message);
            final MessageEvent event2 = MessageEvent.of(sender, message);
            final MessageEvent event3 = MessageEvent.of(sender, message2);
            final MessageEvent event4 = MessageEvent.of(sender, new Object[]{ message });
            final MessageEvent event5 = MessageEvent.of(sender, new Object[]{ message });
            final MessageEvent event6 = MessageEvent.of(sender, new Object[]{ message2 });

            assertEquals(event1, event2);
            assertNotEquals(event1, event3);

            assertEquals(event4, event5);
            assertNotEquals(event4, event6);
        }

        @Test
        void sameBecauseOfEqualPayload() {
            final MessageEvent event1 = MessageEvent.of(sender, "Hallo Welt".getBytes());
            final MessageEvent event2 = MessageEvent.of(sender, "Hallo Welt".getBytes());

            assertEquals(event1, event2);
        }
    }

    @Nested
    class HashCode {
        @Mock
        private Object message2;

        @Test
        void notSameBecauseOfDifferentMessage() {
            final MessageEvent event1 = MessageEvent.of(sender, message);
            final MessageEvent event2 = MessageEvent.of(sender, message);
            final MessageEvent event3 = MessageEvent.of(sender, message2);
            final MessageEvent event4 = MessageEvent.of(sender, new Object[]{ message });
            final MessageEvent event5 = MessageEvent.of(sender, new Object[]{ message });
            final MessageEvent event6 = MessageEvent.of(sender, new Object[]{ message2 });

            assertEquals(event1.hashCode(), event2.hashCode());
            assertNotEquals(event1.hashCode(), event3.hashCode());

            assertEquals(event4.hashCode(), event5.hashCode());
            assertNotEquals(event4.hashCode(), event6.hashCode());
        }
    }

    @Nested
    class Of {
        @Test
        void acceptValidArguments() {
            assertNotNull(MessageEvent.of(sender, null));
        }
    }
}
