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
package org.drasyl.handler.dht.chord;

import org.drasyl.identity.IdentityPublicKey;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.drasyl.handler.dht.chord.ChordUtil.chordId;
import static org.drasyl.handler.dht.chord.ChordUtil.chordIdHex;
import static org.drasyl.handler.dht.chord.ChordUtil.chordIdPosition;
import static org.drasyl.handler.dht.chord.ChordUtil.ithFingerStart;
import static org.drasyl.handler.dht.chord.ChordUtil.relativeChordId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class ChordUtilTest {
    @Nested
    class ChordId {
        @Test
        void shouldReturnCorrectId() {
            assertEquals(278773390, chordId("Hello"));
            assertEquals(1441048492, chordId(true));
            assertEquals(739913095, chordId(false));
            assertEquals(2422852216L, chordId(0));
            assertEquals(2144188888, chordId(IdentityPublicKey.of("8cd8f4ac74d4d249558cd3682580004e35c07664bfedc639e21d1822c9e1ef40")));
            assertEquals(1473882460, chordId(IdentityPublicKey.of("c287d900954115f60894e290bd57862f13a1daca879dc03ffd310ca81d18db33")));
            assertEquals(1848401582, chordId(IdentityPublicKey.of("1e1dc25fbb645f2fdd2bca98cd8b7fda7701e2a47186b91590d6e0700ac94f9d")));
        }
    }

    @Nested
    class ChordIdHex {
        @Test
        void shouldReturnCorrectHexString() {
            assertEquals("109dbe8e", chordIdHex("Hello"));
            assertEquals("55e4a7ac", chordIdHex(true));
            assertEquals("2c1a2d87", chordIdHex(false));
            assertEquals("9069ca78", chordIdHex((Object) 0));
            assertEquals("7fcdb9d8", chordIdHex(IdentityPublicKey.of("8cd8f4ac74d4d249558cd3682580004e35c07664bfedc639e21d1822c9e1ef40")));
            assertEquals("57d9a95c", chordIdHex(IdentityPublicKey.of("c287d900954115f60894e290bd57862f13a1daca879dc03ffd310ca81d18db33")));
            assertEquals("6e2c5eae", chordIdHex(IdentityPublicKey.of("1e1dc25fbb645f2fdd2bca98cd8b7fda7701e2a47186b91590d6e0700ac94f9d")));
        }
    }

    @Nested
    class ChordIdPosition {
        @Test
        void shouldReturnCorrectPosition() {
            assertEquals("6%", chordIdPosition("Hello"));
            assertEquals("33%", chordIdPosition(true));
            assertEquals("17%", chordIdPosition(false));
            assertEquals("56%", chordIdPosition((Object) 0));
            assertEquals("49%", chordIdPosition(IdentityPublicKey.of("8cd8f4ac74d4d249558cd3682580004e35c07664bfedc639e21d1822c9e1ef40")));
            assertEquals("34%", chordIdPosition(IdentityPublicKey.of("c287d900954115f60894e290bd57862f13a1daca879dc03ffd310ca81d18db33")));
            assertEquals("43%", chordIdPosition(IdentityPublicKey.of("1e1dc25fbb645f2fdd2bca98cd8b7fda7701e2a47186b91590d6e0700ac94f9d")));
        }
    }

    @Nested
    class IthFingerStart {
        @Test
        void shouldReturnCorrectId() {
            assertThrows(IllegalArgumentException.class, () -> ithFingerStart(100, 0));
            final long baseId = 3_000_000_000L;
            assertEquals(baseId + 1, ithFingerStart(baseId, 1));
            assertEquals(baseId + 2, ithFingerStart(baseId, 2));
            assertEquals(baseId + 8, ithFingerStart(baseId, 4));
            assertEquals(baseId + 128, ithFingerStart(baseId, 8));
            assertEquals(baseId + 32_768, ithFingerStart(baseId, 16));
            assertEquals((baseId + 2_147_483_648L) % 4_294_967_296L, ithFingerStart(baseId, 32));
        }
    }

    @Nested
    class relativeChordId {
        @Test
        void shouldReturnCorrectPosition() {
            long x = 3_132_692_194L + 1_162_275_102L;
            assertEquals(0, relativeChordId("Hello", "Hello"));
            assertEquals(3_132_692_194L, relativeChordId("Hello", true));
            assertEquals(4_294_967_296L - 3_132_692_194L, relativeChordId(true, "Hello"));
            assertEquals(3_833_827_591L, relativeChordId("Hello", false));
            assertEquals(2_150_888_470L, relativeChordId("Hello", (Object) 0));
            assertEquals(2_429_551_798L, relativeChordId("Hello", IdentityPublicKey.of("8cd8f4ac74d4d249558cd3682580004e35c07664bfedc639e21d1822c9e1ef40")));
            assertEquals(3_099_858_226L, relativeChordId("Hello", IdentityPublicKey.of("c287d900954115f60894e290bd57862f13a1daca879dc03ffd310ca81d18db33")));
            assertEquals(2_725_339_104L, relativeChordId("Hello", IdentityPublicKey.of("1e1dc25fbb645f2fdd2bca98cd8b7fda7701e2a47186b91590d6e0700ac94f9d")));
        }
    }
}
