/*
 * Copyright (c) 2020.
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
package org.drasyl.plugin.groups.manager.data;

import org.drasyl.identity.CompressedPublicKey;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class MemberTest {
    @Mock
    private CompressedPublicKey publicKey;

    @Nested
    class Getters {
        @Test
        void shouldReturnCorrectPublicKey() {
            final Member member = Member.of(publicKey);

            assertEquals(publicKey, member.getPublicKey());

            // ignore toString()
            member.toString();
        }
    }

    @Nested
    class Equals {
        @Test
        void shouldBeEquals() {
            final Member member1 = Member.of(publicKey);
            final Member member2 = Member.of(publicKey);

            assertEquals(member1, member2);
        }

        @Test
        void shouldNotBeEquals() {
            final Member member1 = Member.of(publicKey);
            final Member member2 = Member.of(mock(CompressedPublicKey.class));

            assertNotEquals(member1, member2);
        }
    }

    @Nested
    class HashCode {
        @Test
        void shouldBeEquals() {
            final Member member1 = Member.of(publicKey);
            final Member member2 = Member.of(publicKey);

            assertEquals(member1.hashCode(), member2.hashCode());
        }

        @Test
        void shouldNotBeEquals() {
            final Member member1 = Member.of(publicKey);
            final Member member2 = Member.of(mock(CompressedPublicKey.class));

            assertNotEquals(member1.hashCode(), member2.hashCode());
        }
    }
}