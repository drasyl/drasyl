/*
 * Copyright (c) 2020-2022 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.cli.rc.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.drasyl.cli.node.message.JsonRpc2Error;
import org.drasyl.cli.node.message.JsonRpc2Request;
import org.drasyl.cli.node.message.JsonRpc2Response;
import org.drasyl.serialization.IdentityMixin;
import org.drasyl.serialization.IdentityPublicKeyMixin;
import org.drasyl.serialization.IdentitySecretKeyMixin;
import org.drasyl.serialization.KeyAgreementPublicKeyMixin;
import org.drasyl.serialization.KeyAgreementSecretKeyMixin;
import org.drasyl.serialization.PeerMixin;
import org.drasyl.serialization.PeersListMixin;
import org.drasyl.serialization.ProofOfWorkMixin;
import org.drasyl.serialization.RoleMixin;
import org.drasyl.handler.peers.Peer;
import org.drasyl.handler.peers.PeersList;
import org.drasyl.handler.peers.Role;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.IdentitySecretKey;
import org.drasyl.identity.KeyAgreementPublicKey;
import org.drasyl.identity.KeyAgreementSecretKey;
import org.drasyl.identity.ProofOfWork;

import java.util.Map;

import static io.netty.channel.ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE;
import static org.drasyl.cli.node.message.JsonRpc2Error.METHOD_NOT_FOUND;
import static org.drasyl.node.JsonUtil.JACKSON_MAPPER;

public abstract class JsonRpc2RequestHandler extends SimpleChannelInboundHandler<JsonRpc2Request> {
    protected void requestMethodNotFound(final ChannelHandlerContext ctx,
                                         final JsonRpc2Request request,
                                         final String method) {
        final Object requestId = request.getId();
        final JsonRpc2Error error = new JsonRpc2Error(METHOD_NOT_FOUND, "the method '" + method + "' does not exist / is not available.");
        final JsonRpc2Response response = new JsonRpc2Response(error, requestId);
        ctx.writeAndFlush(response).addListener(FIRE_EXCEPTION_ON_FAILURE);
    }

    protected Map<String, Object> identityMap(final Identity identity) {
        JACKSON_MAPPER.addMixIn(Identity.class, IdentityMixin.class);
        JACKSON_MAPPER.addMixIn(ProofOfWork.class, ProofOfWorkMixin.class);
        JACKSON_MAPPER.addMixIn(IdentityPublicKey.class, IdentityPublicKeyMixin.class);
        JACKSON_MAPPER.addMixIn(IdentitySecretKey.class, IdentitySecretKeyMixin.class);
        JACKSON_MAPPER.addMixIn(KeyAgreementPublicKey.class, KeyAgreementPublicKeyMixin.class);
        JACKSON_MAPPER.addMixIn(KeyAgreementSecretKey.class, KeyAgreementSecretKeyMixin.class);

        return JACKSON_MAPPER.convertValue(identity, Map.class);
    }

    protected Map<String, Object> peersMap(final PeersList peers) {
        JACKSON_MAPPER.addMixIn(PeersList.class, PeersListMixin.class);
        JACKSON_MAPPER.addMixIn(IdentityPublicKey.class, IdentityPublicKeyMixin.class);
        JACKSON_MAPPER.addMixIn(Peer.class, PeerMixin.class);
        JACKSON_MAPPER.addMixIn(Role.class, RoleMixin.class);

        return JACKSON_MAPPER.convertValue(peers, Map.class);
    }
}
