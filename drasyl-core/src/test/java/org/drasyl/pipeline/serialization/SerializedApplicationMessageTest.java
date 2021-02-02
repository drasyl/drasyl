/*
 * Copyright (c) 2021.
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
package org.drasyl.pipeline.serialization;

import org.drasyl.identity.CompressedPublicKey;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(MockitoExtension.class)
class SerializedApplicationMessageTest {
    @Nested
    class Equals {
        @Test
        void shouldRecognizeEqualMessages(@Mock final CompressedPublicKey sender,
                                          @Mock final CompressedPublicKey recipient) {
            final SerializedApplicationMessage messageA = new SerializedApplicationMessage(sender, recipient, String.class, "Hello".getBytes());
            final SerializedApplicationMessage messageB = new SerializedApplicationMessage(sender, recipient, String.class, "Hello".getBytes());
            final SerializedApplicationMessage messageC = new SerializedApplicationMessage(sender, recipient, String.class, "Bye".getBytes());

            assertEquals(messageA, messageA);
            assertEquals(messageA, messageB);
            assertEquals(messageB, messageA);
            assertNotEquals(null, messageA);
            assertNotEquals(messageA, messageC);
            assertNotEquals(messageC, messageA);
        }
    }

    @Nested
    class HashCode {
        @Test
        void shouldRecognizeEqualMessages(@Mock final CompressedPublicKey sender,
                                          @Mock final CompressedPublicKey recipient) {
            final SerializedApplicationMessage messageA = new SerializedApplicationMessage(sender, recipient, String.class, "Hello".getBytes());
            final SerializedApplicationMessage messageB = new SerializedApplicationMessage(sender, recipient, String.class, "Hello".getBytes());
            final SerializedApplicationMessage messageC = new SerializedApplicationMessage(sender, recipient, String.class, "Bye".getBytes());

            assertEquals(messageA.hashCode(), messageB.hashCode());
            assertNotEquals(messageA.hashCode(), messageC.hashCode());
            assertNotEquals(messageB.hashCode(), messageC.hashCode());
        }
    }

    @Nested
    class ToString {
        @Test
        void shouldReturnCorrectString(@Mock final CompressedPublicKey sender,
                                       @Mock final CompressedPublicKey recipient) {
            final String string = new SerializedApplicationMessage(sender, recipient, String.class, "Hello".getBytes()).toString();

            assertNotNull(string);
        }
    }
}