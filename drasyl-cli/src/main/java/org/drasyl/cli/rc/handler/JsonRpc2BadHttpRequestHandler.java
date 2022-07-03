package org.drasyl.cli.rc.handler;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.handler.timeout.ReadTimeoutHandler;

import static io.netty.handler.codec.http.HttpHeaderNames.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderValues.APPLICATION_JSON;
import static io.netty.handler.codec.http.HttpHeaderValues.CLOSE;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Together used with {@link ReadTimeoutHandler} allowing to handle clients not sending a HTTP
 * request within a certain time.
 */
public class JsonRpc2BadHttpRequestHandler extends ChannelInboundHandlerAdapter {
    private static final ByteBuf BAD_REQUEST_BUF = Unpooled.copiedBuffer("{\"jsonrpc\": \"2.0\", \"error\": {\"code\": 400, \"message\": \"Bad Request\"}, \"id\": null}\n", UTF_8).asReadOnly();

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx,
                                final Throwable cause) {
        if (cause instanceof ReadTimeoutException) {
            final HttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, BAD_REQUEST, BAD_REQUEST_BUF.retain());
            response.headers()
                    .set(CONTENT_TYPE, APPLICATION_JSON)
                    .set(CONTENT_LENGTH, BAD_REQUEST_BUF.readableBytes())
                    .set(CONNECTION, CLOSE);
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        }
        else {
            ctx.fireExceptionCaught(cause);
        }
    }
}
