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
package org.drasyl.peer.connection.server.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.peer.connection.message.IamMessage;
import org.drasyl.peer.connection.message.WhoAreYouMessage;
import org.drasyl.peer.connection.message.WhoisMessage;

/**
 * This handler returns the public key of this peer to an empty {@link WhoisMessage#getRequester()}.
 * This allows the client to issue a join proof for this peer.
 */
public class WhoAmIHandler extends SimpleChannelInboundHandler<WhoAreYouMessage> {
    public static final String WHO_AM_I = "whoAmI";
    private final CompressedPublicKey myPublicKey;

    public WhoAmIHandler(final CompressedPublicKey myPublicKey) {
        this.myPublicKey = myPublicKey;
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx,
                                final WhoAreYouMessage msg) throws Exception {
        ctx.writeAndFlush(new IamMessage(myPublicKey, msg.getId()));
    }
}