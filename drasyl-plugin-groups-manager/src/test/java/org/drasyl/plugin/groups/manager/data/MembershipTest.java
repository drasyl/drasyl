/*
 * Copyright (c) 2020-2021.
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
