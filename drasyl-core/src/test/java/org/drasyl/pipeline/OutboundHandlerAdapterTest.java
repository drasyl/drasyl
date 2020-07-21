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

import org.drasyl.identity.CompressedPublicKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OutboundHandlerAdapterTest {
    @Mock
    private HandlerContext ctx;

    @Test
    void shouldPassthroughsOnWrite() {
        OutboundHandlerAdapter adapter = new OutboundHandlerAdapter();

        CompressedPublicKey recipient = mock(CompressedPublicKey.class);
        Object msg = mock(Object.class);
        CompletableFuture<Void> future = mock(CompletableFuture.class);

        adapter.write(ctx, recipient, msg, future);

        verify(ctx).write(eq(recipient), eq(msg), eq(future));
    }
}