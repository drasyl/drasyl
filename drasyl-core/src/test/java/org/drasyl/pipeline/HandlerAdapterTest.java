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
package org.drasyl.pipeline;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class HandlerAdapterTest {
    @Mock
    private HandlerContext ctx;

    @Test
    void shouldDoNothing() {
        HandlerAdapter adapter = new HandlerAdapter() {
            @Override
            public void handlerAdded(HandlerContext ctx) {
                super.handlerAdded(ctx);
            }

            @Override
            public void handlerRemoved(HandlerContext ctx) {
                super.handlerRemoved(ctx);
            }
        };

        adapter.handlerAdded(ctx);
        adapter.handlerRemoved(ctx);

        verifyNoInteractions(ctx);
    }
}