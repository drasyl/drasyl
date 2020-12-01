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
package org.drasyl.remote.handler;

import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.codec.ObjectHolder;
import org.drasyl.pipeline.message.ApplicationMessage;
import org.drasyl.pipeline.skeleton.SimpleInboundHandler;
import org.drasyl.remote.message.RemoteApplicationMessage;

import java.util.concurrent.CompletableFuture;

public class RemoteMessage2ApplicationMessageHandler extends SimpleInboundHandler<RemoteApplicationMessage, Address> {
    public static final RemoteMessage2ApplicationMessageHandler INSTANCE = new RemoteMessage2ApplicationMessageHandler();
    public static final String REMOTE_MESSAGE_2_APPLICATION_MESSAGE_HANDLER = "REMOTE_MESSAGE_2_APPLICATION_MESSAGE_HANDLER";

    private RemoteMessage2ApplicationMessageHandler() {
    }

    @Override
    protected void matchedRead(final HandlerContext ctx,
                               final Address sender,
                               final RemoteApplicationMessage msg,
                               final CompletableFuture<Void> future) {
        ctx.fireRead(sender, new ApplicationMessage(msg.getSender(), msg.getRecipient(), ObjectHolder.of(msg.getType(), msg.getPayload())), future);
    }
}
