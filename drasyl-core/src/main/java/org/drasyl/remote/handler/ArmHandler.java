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
package org.drasyl.remote.handler;

import com.google.protobuf.MessageLite;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.Stateless;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.handler.codec.MessageToMessageCodec;
import org.drasyl.remote.protocol.RemoteEnvelope;

import java.util.List;

/**
 * Arms (sign/encrypt) outbound and disarms (verify/decrypt) inbound messages. Considers only
 * messages that are addressed from or to us. Messages that could not be (dis)armed) are dropped.
 */
@Stateless
@SuppressWarnings({ "java:S110" })
public final class ArmHandler extends MessageToMessageCodec<RemoteEnvelope<? extends MessageLite>, RemoteEnvelope<? extends MessageLite>, Address> {
    public static final ArmHandler INSTANCE = new ArmHandler();

    private ArmHandler() {
        // singleton
    }

    @Override
    protected void decode(final HandlerContext ctx,
                          final Address sender,
                          final RemoteEnvelope<? extends MessageLite> msg,
                          final List<Object> out) throws Exception {
        if (ctx.identity().getPublicKey().equals(msg.getRecipient())) {
            // disarm all messages addressed to us
            final RemoteEnvelope<? extends MessageLite> disarmedMsg = msg.disarmAndRelease(ctx.identity().getPrivateKey());
            out.add(disarmedMsg.retain());
        }
        else {
            out.add(msg.retain());
        }
    }

    @Override
    protected void encode(final HandlerContext ctx,
                          final Address recipient,
                          final RemoteEnvelope<? extends MessageLite> msg,
                          final List<Object> out) throws Exception {
        if (ctx.identity().getPublicKey().equals(msg.getSender())) {
            // arm all messages from us
            final RemoteEnvelope<? extends MessageLite> armedMsg = msg.armAndRelease(ctx.identity().getPrivateKey());
            out.add(armedMsg.retain());
        }
        else {
            out.add(msg.retain());
        }
    }
}
