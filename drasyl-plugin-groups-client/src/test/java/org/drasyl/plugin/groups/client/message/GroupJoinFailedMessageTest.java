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
 *  along with drasyl.  If not, see <http://www.gnu.org/licenses />.
 */
package org.drasyl.plugin.groups.client.message;

import org.drasyl.plugin.groups.client.Group;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.drasyl.plugin.groups.client.message.GroupJoinFailedMessage.Error.ERROR_GROUP_NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class GroupJoinFailedMessageTest {
    @Nested
    class Constructor {
        @Test
        void shouldRejectNullValues() {
            assertThrows(NullPointerException.class, () -> new GroupJoinFailedMessage(Group.of("my-squad"), null));
            assertThrows(NullPointerException.class, () -> new GroupJoinFailedMessage(null, ERROR_GROUP_NOT_FOUND));
        }

        @Test
        void shouldReturnCorrectReason() {
            final GroupJoinFailedMessage msg = new GroupJoinFailedMessage(Group.of("my-squad"), ERROR_GROUP_NOT_FOUND);

            assertEquals(ERROR_GROUP_NOT_FOUND, msg.getReason());
            assertEquals(ERROR_GROUP_NOT_FOUND.getDescription(), msg.getReason().getDescription());
        }

        @Test
        void shouldReturnCorrectGroupName() {
            final GroupJoinFailedMessage msg = new GroupJoinFailedMessage(Group.of("my-squad"), ERROR_GROUP_NOT_FOUND);

            assertEquals(Group.of("my-squad"), msg.getGroup());
        }

        @Test
        void shouldGenerateCorrectErrorEnum() {
            final GroupJoinFailedMessage.Error error = ERROR_GROUP_NOT_FOUND;

            assertEquals(error, GroupJoinFailedMessage.Error.from(error.getDescription()));
        }
    }

    @Nested
    class Equals {
        @Test
        void shouldBeEquals() {
            final GroupJoinFailedMessage msg1 = new GroupJoinFailedMessage(Group.of("my-squad"), ERROR_GROUP_NOT_FOUND);
            final GroupJoinFailedMessage msg2 = new GroupJoinFailedMessage(Group.of("my-squad"), ERROR_GROUP_NOT_FOUND);

            assertEquals(msg1, msg2);
        }

        @Test
        void shouldNotBeEquals() {
            final GroupJoinFailedMessage msg1 = new GroupJoinFailedMessage(Group.of("my-squad"), ERROR_GROUP_NOT_FOUND);
            final GroupJoinFailedMessage msg2 = new GroupJoinFailedMessage(Group.of("your-squad"), ERROR_GROUP_NOT_FOUND);

            assertNotEquals(msg1, msg2);
        }
    }

    @Nested
    class HashCode {
        @Test
        void shouldBeEquals() {
            final GroupJoinFailedMessage msg1 = new GroupJoinFailedMessage(Group.of("my-squad"), ERROR_GROUP_NOT_FOUND);
            final GroupJoinFailedMessage msg2 = new GroupJoinFailedMessage(Group.of("my-squad"), ERROR_GROUP_NOT_FOUND);

            assertEquals(msg1.hashCode(), msg2.hashCode());
        }

        @Test
        void shouldNotBeEquals() {
            final GroupJoinFailedMessage msg1 = new GroupJoinFailedMessage(Group.of("my-squad"), ERROR_GROUP_NOT_FOUND);
            final GroupJoinFailedMessage msg2 = new GroupJoinFailedMessage(Group.of("your-squad"), ERROR_GROUP_NOT_FOUND);

            assertNotEquals(msg1.hashCode(), msg2.hashCode());
        }
    }
}