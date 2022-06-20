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
    protected void requestMethodNotFound(ChannelHandlerContext ctx, JsonRpc2Request request, String method) {
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
