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
package org.drasyl.peer.connection.superpeer;

import io.netty.channel.ChannelPipeline;
import org.drasyl.peer.connection.client.ClientEnvironment;
import org.drasyl.peer.connection.client.DefaultClientChannelInitializer;

import static org.drasyl.peer.connection.handler.PingPongHandler.PING_PONG_HANDLER;

public class TestClientChannelInitializer extends DefaultClientChannelInitializer {
    private final boolean doPingPong;
    private final boolean doJoin;

    public TestClientChannelInitializer(ClientEnvironment environment, boolean doPingPong, boolean doJoin) {
        super(environment);
        this.doPingPong = doPingPong;
        this.doJoin = doJoin;
    }

    @Override
    protected void idleStage(ChannelPipeline pipeline) {
        super.idleStage(pipeline);

        if (!doPingPong) {
            pipeline.remove(PING_PONG_HANDLER);
        }
    }

    @Override
    protected void customStage(ChannelPipeline pipeline) {
        super.customStage(pipeline);

        if (!doJoin) {
            pipeline.remove(DRASYL_HANDSHAKE_AFTER_WEBSOCKET_HANDSHAKE);
        }
    }
}
