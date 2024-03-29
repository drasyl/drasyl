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
            assertEquals(1_516_911_440L, chordId("Hello"));
            assertEquals(3_612_140_399L, chordId(true));
            assertEquals(2_905_308_945L, chordId(false));
            assertEquals(3_745_472_920L, chordId(0));
            assertEquals(1_070_315_963L, chordId(IdentityPublicKey.of("8cd8f4ac74d4d249558cd3682580004e35c07664bfedc639e21d1822c9e1ef40")));
            assertEquals(3_806_553_154L, chordId(IdentityPublicKey.of("c287d900954115f60894e290bd57862f13a1daca879dc03ffd310ca81d18db33")));
            assertEquals(230_329_742L, chordId(IdentityPublicKey.of("1e1dc25fbb645f2fdd2bca98cd8b7fda7701e2a47186b91590d6e0700ac94f9d")));
        }
    }

    @Nested
    class ChordIdHex {
        @Test
        void shouldReturnCorrectHexString() {
            assertEquals("5a6a3b50", chordIdHex("Hello"));
            assertEquals("d74ce36f", chordIdHex(true));
            assertEquals("ad2b7f11", chordIdHex(false));
            assertEquals("df3f6198", chordIdHex((Object) 0));
            assertEquals("3fcbb9bb", chordIdHex(IdentityPublicKey.of("8cd8f4ac74d4d249558cd3682580004e35c07664bfedc639e21d1822c9e1ef40")));
            assertEquals("e2e36442", chordIdHex(IdentityPublicKey.of("c287d900954115f60894e290bd57862f13a1daca879dc03ffd310ca81d18db33")));
            assertEquals("0dba8d8e", chordIdHex(IdentityPublicKey.of("1e1dc25fbb645f2fdd2bca98cd8b7fda7701e2a47186b91590d6e0700ac94f9d")));
        }
    }

    @Nested
    class ChordIdPosition {
        @Test
        void shouldReturnCorrectPosition() {
            assertEquals("35%", chordIdPosition("Hello"));
            assertEquals("84%", chordIdPosition(true));
            assertEquals("67%", chordIdPosition(false));
            assertEquals("87%", chordIdPosition((Object) 0));
            assertEquals("24%", chordIdPosition(IdentityPublicKey.of("8cd8f4ac74d4d249558cd3682580004e35c07664bfedc639e21d1822c9e1ef40")));
            assertEquals("88%", chordIdPosition(IdentityPublicKey.of("c287d900954115f60894e290bd57862f13a1daca879dc03ffd310ca81d18db33")));
            assertEquals("5%", chordIdPosition(IdentityPublicKey.of("1e1dc25fbb645f2fdd2bca98cd8b7fda7701e2a47186b91590d6e0700ac94f9d")));
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
    class RelativeChordId {
        @Test
        void shouldReturnCorrectPosition() {
            assertEquals(0, relativeChordId("Hello", "Hello"));
            assertEquals(2_199_738_337L, relativeChordId("Hello", true));
            assertEquals(4_294_967_296L - 2_199_738_337L, relativeChordId(true, "Hello"));
            assertEquals(2_906_569_791L, relativeChordId("Hello", false));
            assertEquals(2_066_405_816L, relativeChordId("Hello", (Object) 0));
            assertEquals(446_595_477L, relativeChordId("Hello", IdentityPublicKey.of("8cd8f4ac74d4d249558cd3682580004e35c07664bfedc639e21d1822c9e1ef40")));
            assertEquals(2_005_325_582L, relativeChordId("Hello", IdentityPublicKey.of("c287d900954115f60894e290bd57862f13a1daca879dc03ffd310ca81d18db33")));
            assertEquals(1_286_581_698L, relativeChordId("Hello", IdentityPublicKey.of("1e1dc25fbb645f2fdd2bca98cd8b7fda7701e2a47186b91590d6e0700ac94f9d")));
        }
    }
}
