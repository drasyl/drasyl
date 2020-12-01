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
package org.drasyl.remote.message;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@ExtendWith(MockitoExtension.class)
class UserAgentTest {
    public static final UserAgent FULL_USER_AGENT = new UserAgent("drasyl/1.0.0 (78ef186c) (Mac OS X 10.15.7; x86_64; Java/14:2020-07-14)");
    public static final UserAgent NO_COMMENTS_USER_AGENT = new UserAgent("drasyl/1.0.0 (78ef186c) ()");
    public static final UserAgent VERSION_ONLY_USER_AGENT = new UserAgent("drasyl/1.0.0 (78ef186c)");
    public static final UserAgent EMPTY_USER_AGENT = new UserAgent("");

    @Nested
    class ToString {
        @Test
        void shouldReturnUserAgent() {
            assertEquals("drasyl/1.0.0 (78ef186c) (Mac OS X 10.15.7; x86_64; Java/14:2020-07-14)", new UserAgent("drasyl/1.0.0 (78ef186c) (Mac OS X 10.15.7; x86_64; Java/14:2020-07-14)").toString());
        }
    }

    @Nested
    class GetDrasylVersion {
        @Test
        void shouldReturnVersionForFullUserAgent() {
            assertEquals("1.0.0 (78ef186c)", FULL_USER_AGENT.getDrasylVersion());
        }

        @Test
        void shouldReturnVersionForNoCommentsUserAgent() {
            assertEquals("1.0.0 (78ef186c)", NO_COMMENTS_USER_AGENT.getDrasylVersion());
        }

        @Test
        void shouldReturnVersionForVersionOnlyUserAgent() {
            assertEquals("1.0.0 (78ef186c)", VERSION_ONLY_USER_AGENT.getDrasylVersion());
        }

        @Test
        void shouldReturnNullForEmptyUserAgent() {
            assertNull(EMPTY_USER_AGENT.getDrasylVersion());
        }
    }

    @Nested
    class GetComments {
        @Test
        void shouldReturnVersionForFullUserAgent() {
            assertThat(FULL_USER_AGENT.getComments(), containsInAnyOrder("Mac OS X 10.15.7", "x86_64", "Java/14:2020-07-14"));
        }

        @Test
        void shouldReturnVersionForNoCommentsUserAgent() {
            assertThat(NO_COMMENTS_USER_AGENT.getComments(), is(empty()));
        }

        @Test
        void shouldReturnVersionForVersionOnlyUserAgent() {
            assertThat(VERSION_ONLY_USER_AGENT.getComments(), is(empty()));
        }

        @Test
        void shouldReturnNullForEmptyUserAgent() {
            assertThat(EMPTY_USER_AGENT.getComments(), is(empty()));
        }
    }
}