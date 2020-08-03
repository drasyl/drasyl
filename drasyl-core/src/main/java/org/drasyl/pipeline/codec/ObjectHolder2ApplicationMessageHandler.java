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
package org.drasyl.pipeline.codec;

import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.SimpleOutboundHandler;

import java.util.concurrent.CompletableFuture;

public class ObjectHolder2ApplicationMessageHandler extends SimpleOutboundHandler<ObjectHolder> {
    public static final ObjectHolder2ApplicationMessageHandler INSTANCE = new ObjectHolder2ApplicationMessageHandler();
    public static final String OBJECT_HOLDER2APP_MSG = "objectHolder2ApplicationMessageHandler";

    private ObjectHolder2ApplicationMessageHandler() {
    }

    @Override
    protected void matchedWrite(HandlerContext ctx,
                                CompressedPublicKey recipient,
                                ObjectHolder msg,
                                CompletableFuture<Void> future) {
        ctx.write(recipient, new ApplicationMessage(ctx.identity().getPublicKey(), recipient, msg.getObject(), msg.getClazz()), future);
    }
}
