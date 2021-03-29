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
package org.drasyl.pipeline;

import org.drasyl.DrasylConfig;
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.serialization.Serialization;
import org.drasyl.util.scheduler.DrasylScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class DefaultHandlerContextTest {
    @Mock
    private Handler handler;
    @Mock
    private DrasylConfig config;
    @Mock
    private Pipeline pipeline;
    @Mock
    private DrasylScheduler dependentScheduler;
    @Mock
    private DrasylScheduler independentScheduler;
    @Mock
    private Identity identity;
    @Mock
    private PeersManager peersManager;
    @Mock
    private Serialization inboundSerialization;
    @Mock
    private Serialization outboundSerialization;

    @Test
    void shouldCreateDefaultHandler() {
        final DefaultHandlerContext ctx = new DefaultHandlerContext("ctx", handler, config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization);

        assertEquals(handler, ctx.handler());
    }
}
