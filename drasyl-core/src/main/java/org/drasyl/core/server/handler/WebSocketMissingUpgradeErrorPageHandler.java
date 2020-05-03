package org.drasyl.core.server.handler;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.CharsetUtil;
import org.drasyl.core.node.DrasylNode;
import org.drasyl.core.node.identity.Identity;
import org.drasyl.core.node.identity.IdentityManager;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderNames.SERVER;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpResponseStatus.*;

/**
 * This handler returns an HTML error page if the HTTP request does not perform a Websocket
 * upgrade.
 */
public class WebSocketMissingUpgradeErrorPageHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private final IdentityManager identityManager;

    public WebSocketMissingUpgradeErrorPageHandler(IdentityManager identityManager) {
        this.identityManager = identityManager;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) {
        // pass through websocket request
        if (req.headers().containsValue(HttpHeaderNames.CONNECTION, HttpHeaderValues.UPGRADE, true)
                || HttpHeaderValues.WEBSOCKET.contentEqualsIgnoreCase(req.headers().get(HttpHeaderNames.UPGRADE))) {
            ctx.fireChannelRead(req.retain());
            return;
        }

        // allow only GET request
        if (!GET.equals(req.method())) {
            sendHttpResponse(ctx, new DefaultFullHttpResponse(req.protocolVersion(), FORBIDDEN,
                    ctx.alloc().buffer(0)));
            return;
        }

        if ("/".equals(req.uri()) || "/index.html".equals(req.uri()) || "/index.htm".equals(req.uri())) {
            // display custom bad request error page for root path
            Identity identity = identityManager.getIdentity();
            ByteBuf content = getContent(identity);
            FullHttpResponse res = new DefaultFullHttpResponse(req.protocolVersion(), BAD_REQUEST, content);
            res.headers().set("x-identity", identity.getId());
            res.headers().set(CONTENT_TYPE, "text/html; charset=UTF-8");
            HttpUtil.setContentLength(res, content.readableBytes());
            sendHttpResponse(ctx, res);
        }
        else {
            // return "not found" for all other pathes
            sendHttpResponse(ctx, new DefaultFullHttpResponse(req.protocolVersion(), NOT_FOUND,
                    ctx.alloc().buffer(0)));
        }
    }

    private static void sendHttpResponse(ChannelHandlerContext ctx,
                                         FullHttpResponse res) {
        res.headers().set(SERVER, "drasyl/" + DrasylNode.getVersion());

        // add http code reason phrase if content is empty
        if (res.content().readableBytes() == 0) {
            HttpResponseStatus responseStatus = res.status();
            ByteBufUtil.writeUtf8(res.content(), responseStatus.toString());
            HttpUtil.setContentLength(res, res.content().readableBytes());
        }
        // Send the response and close the connection
        ChannelFuture future = ctx.writeAndFlush(res);
        future.addListener(ChannelFutureListener.CLOSE);
    }

    public static ByteBuf getContent(Identity identity) {
        return Unpooled.copiedBuffer(
                "\n" +
                        "<!DOCTYPE HTML PUBLIC \"-//IETF//DTD HTML 2.0//EN\">\n" +
                        "<html><head>\n" +
                        "<title>400 Bad Request</title>\n" +
                        "</head><body>\n" +
                        "<h1>Bad Request</h1>\n" +
                        "<p>Not a WebSocket Handshake Request: Missing Upgrade.</p>\n" +
                        "<hr>\n" +
                        "<address>drasyl/" + DrasylNode.getVersion() + " with Identity " + identity.getId() + "</address>\n" +
                        "</body></html>\n", CharsetUtil.UTF_8);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }

    private static String getWebSocketLocation(ChannelPipeline cp, HttpRequest req, String path) {
        String protocol = "ws";
        if (cp.get(SslHandler.class) != null) {
            // SSL in use so use Secure WebSockets
            protocol = "wss";
        }
        return protocol + "://" + req.headers().get(HttpHeaderNames.HOST) + path;
    }
}
