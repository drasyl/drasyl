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
import org.drasyl.identity.Identity;

import java.util.Map;

import static io.netty.channel.ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE;
import static org.drasyl.cli.node.message.JsonRpc2Error.METHOD_NOT_FOUND;

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
        return Map.of(
                "proofOfWork", identity.getProofOfWork().intValue(),
                "identityKeyPair", Map.of(
                        "publicKey", identity.getIdentityPublicKey().toString(),
                        "secretKey", identity.getIdentitySecretKey().toUnmaskedString()
                ),
                "agreementKeyPair", Map.of(
                        "publicKey", identity.getKeyAgreementPublicKey().toString(),
                        "secretKey", identity.getKeyAgreementSecretKey().toUnmaskedString()
                )
        );
    }
}
