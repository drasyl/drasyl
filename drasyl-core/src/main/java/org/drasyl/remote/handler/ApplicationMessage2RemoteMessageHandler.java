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
import org.drasyl.pipeline.message.ApplicationMessage;
import org.drasyl.pipeline.skeleton.SimpleOutboundHandler;
import org.drasyl.remote.message.RemoteApplicationMessage;

import java.util.concurrent.CompletableFuture;

public class ApplicationMessage2RemoteMessageHandler extends SimpleOutboundHandler<ApplicationMessage, Address> {
    public static final ApplicationMessage2RemoteMessageHandler INSTANCE = new ApplicationMessage2RemoteMessageHandler();
    public static final String APPLICATION_MESSAGE_2_REMOTE_MESSAGE_HANDLER = "APPLICATION_MESSAGE_2_REMOTE_MESSAGE_HANDLER";

    private ApplicationMessage2RemoteMessageHandler() {
    }

    @Override
    protected void matchedWrite(final HandlerContext ctx,
                                final Address recipient,
                                final ApplicationMessage msg,
                                final CompletableFuture<Void> future) {
        final RemoteApplicationMessage remoteMessage = new RemoteApplicationMessage(ctx.config().getNetworkId(), ctx.identity().getPublicKey(), ctx.identity().getProofOfWork(), msg.getRecipient(), msg.getContent().getClazzAsString(), msg.getContent().getObject());
        ctx.write(recipient, remoteMessage, future);
    }
}
