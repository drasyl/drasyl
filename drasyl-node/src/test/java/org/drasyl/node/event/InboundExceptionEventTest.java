/*
 * Copyright (c) 2020-2023.
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
package org.drasyl.node.event;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

@ExtendWith(MockitoExtension.class)
public class InboundExceptionEventTest {
    @Nested
    class ToString {
        @Test
        void shouldIncludeStacktraceOfEmbeddedException() throws Throwable {
            final Throwable error = new RuntimeException("whoops!");
            final InboundExceptionEvent event = InboundExceptionEvent.of(error);

            // actual exception?
            assertThat(event.toString(), containsString("java.lang.RuntimeException: whoops!"));

            // stacktrace?
            assertThat(event.toString(), containsString("at org.drasyl"));
        }
    }
}
