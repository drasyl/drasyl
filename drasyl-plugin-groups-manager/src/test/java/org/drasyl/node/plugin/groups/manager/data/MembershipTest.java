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
package org.drasyl.node.plugin.groups.manager.data;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@ExtendWith(MockitoExtension.class)
class MembershipTest {
    @Mock
    private Member member;
    @Mock
    private Group group;

    @Nested
    class Getters {
        @Test
        void shouldReturnCorrectMember() {
            final Membership membership = Membership.of(member, group, 0);

            assertEquals(member, membership.getMember());
        }

        @Test
        void shouldReturnCorrectGroup() {
            final Membership membership = Membership.of(member, group, 0);

            assertEquals(group, membership.getGroup());
        }

        @Test
        void shouldReturnCorrectStaleAt() {
            final Membership membership = Membership.of(member, group, 0);

            assertEquals(0, membership.getStaleAt());
        }
    }

    @Nested
    class Equals {
        @Test
        void shouldBeEquals() {
            final Membership membership1 = Membership.of(member, group, 0);
            final Membership membership2 = Membership.of(member, group, 0);

            assertEquals(membership1, membership2);
        }

        @Test
        void shouldNotBeEquals(@Mock final Member member2) {
            final Membership membership1 = Membership.of(member, group, 0);
            final Membership membership2 = Membership.of(member2, group, 0);

            assertNotEquals(membership1, membership2);
        }
    }

    @Nested
    class HashCode {
        @Test
        void shouldBeEquals() {
            final Membership membership1 = Membership.of(member, group, 0);
            final Membership membership2 = Membership.of(member, group, 0);

            assertEquals(membership1.hashCode(), membership2.hashCode());
        }

        @Test
        void shouldNotBeEquals(@Mock final Member member2) {
            final Membership membership1 = Membership.of(member, group, 0);
            final Membership membership2 = Membership.of(member2, group, 0);

            assertNotEquals(membership1.hashCode(), membership2.hashCode());
        }
    }
}
