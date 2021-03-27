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
package org.drasyl.pipeline;

import io.netty.channel.EventLoopGroup;
import org.drasyl.DrasylConfig;
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeersManager;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.drasyl.pipeline.HandlerMask.ALL;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@ExtendWith(MockitoExtension.class)
class VisualPipelineTest {
    @Nested
    class PrintInboundOrder {
        @Test
        void shouldNotFail(@Mock final DrasylConfig config,
                           @Mock final Identity identity,
                           @Mock final PeersManager peersManager,
                           @Mock final EventLoopGroup bossGroup,
                           @Mock final EventLoopGroup workerGroup) {
            final DrasylPipeline pipeline = new DrasylPipeline(event -> {
            }, config, identity, peersManager, bossGroup, workerGroup);

            assertDoesNotThrow(() -> VisualPipeline.printInboundOrder(pipeline, ALL));
        }
    }

    @Nested
    class PrintOutboundOrder {
        @Test
        void shouldNotFail(@Mock final DrasylConfig config,
                           @Mock final Identity identity,
                           @Mock final PeersManager peersManager,
                           @Mock final EventLoopGroup bossGroup,
                           @Mock final EventLoopGroup workerGroup) {
            final DrasylPipeline pipeline = new DrasylPipeline(event -> {
            }, config, identity, peersManager, bossGroup, workerGroup);

            assertDoesNotThrow(() -> VisualPipeline.printOutboundOrder(pipeline, ALL));
        }
    }

    @Nested
    class PrintOnlySimpleInboundHandler {
        @Test
        void shouldNotFail(@Mock final DrasylConfig config,
                           @Mock final Identity identity,
                           @Mock final PeersManager peersManager,
                           @Mock final EventLoopGroup bossGroup,
                           @Mock final EventLoopGroup workerGroup) {
            final DrasylPipeline pipeline = new DrasylPipeline(event -> {
            }, config, identity, peersManager, bossGroup, workerGroup);

            assertDoesNotThrow(() -> VisualPipeline.printOnlySimpleInboundHandler(pipeline, ALL));
        }
    }

    @Nested
    class PrintOnlySimpleOutboundHandler {
        @Test
        void shouldNotFail(@Mock final DrasylConfig config,
                           @Mock final Identity identity,
                           @Mock final PeersManager peersManager,
                           @Mock final EventLoopGroup bossGroup,
                           @Mock final EventLoopGroup workerGroup) {
            final DrasylPipeline pipeline = new DrasylPipeline(event -> {
            }, config, identity, peersManager, bossGroup, workerGroup);

            assertDoesNotThrow(() -> VisualPipeline.printOnlySimpleOutboundHandler(pipeline, ALL));
        }
    }
}
