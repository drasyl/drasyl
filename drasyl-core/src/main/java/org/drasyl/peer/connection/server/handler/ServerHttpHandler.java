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

import com.fasterxml.jackson.core.JsonProcessingException;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.util.CharsetUtil;
import org.drasyl.DrasylNode;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.peer.PeersManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderNames.SERVER;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpMethod.HEAD;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static org.drasyl.util.JSONUtil.JACKSON_WRITER;

/**
 * This handler returns an HTML error page if the HTTP request does not perform a Websocket
 * upgrade.
 */
public class ServerHttpHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private static final Logger LOG = LoggerFactory.getLogger(ServerHttpHandler.class);
    private final int networkId;
    private final CompressedPublicKey publicKey;
    private final PeersManager peersManager;

    public ServerHttpHandler(final int networkId,
                             final CompressedPublicKey publicKey,
                             final PeersManager peersManager) {
        this.networkId = networkId;
        this.publicKey = publicKey;
        this.peersManager = peersManager;
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, final FullHttpRequest req) {
        // pass through websocket request
        if (req.headers().containsValue(HttpHeaderNames.CONNECTION, HttpHeaderValues.UPGRADE, true)
                || HttpHeaderValues.WEBSOCKET.contentEqualsIgnoreCase(req.headers().get(HttpHeaderNames.UPGRADE))) {
            ctx.fireChannelRead(req.retain());
            return;
        }

        // response with node information on HEAD request
        if (HEAD.equals(req.method())) {
            generateHeaders(ctx, req, networkId, publicKey, OK);
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
            generateHeaders(ctx, req, networkId, publicKey, BAD_REQUEST);
        }
        else if ("/peers.json".equals(req.uri())) {
            final DefaultFullHttpResponse res = new DefaultFullHttpResponse(req.protocolVersion(), OK,
                    getPeers(peersManager));
            res.headers().set("x-network-id", networkId);
            res.headers().set("x-public-key", publicKey);
            res.headers().set(CONTENT_TYPE, "application/json; charset=UTF-8");
            sendHttpResponse(ctx, res);
        }
        else {
            // return "not found" for all other paths
            sendHttpResponse(ctx, new DefaultFullHttpResponse(req.protocolVersion(), NOT_FOUND,
                    ctx.alloc().buffer(0)));
        }
    }

    private static void generateHeaders(final ChannelHandlerContext ctx,
                                        final FullHttpRequest req,
                                        final int networkId,
                                        final CompressedPublicKey publicKey,
                                        final HttpResponseStatus status) {
        final ByteBuf content = getContent(networkId, publicKey);
        final FullHttpResponse res = new DefaultFullHttpResponse(req.protocolVersion(), status, content);
        res.headers().set("x-network-id", networkId);
        res.headers().set("x-public-key", publicKey);
        res.headers().set(CONTENT_TYPE, "text/html; charset=UTF-8");
        HttpUtil.setContentLength(res, content.readableBytes());
        sendHttpResponse(ctx, res);
    }

    private static void sendHttpResponse(final ChannelHandlerContext ctx,
                                         final FullHttpResponse res) {
        res.headers().set(SERVER, "drasyl/" + DrasylNode.getVersion());

        // add http code reason phrase if content is empty
        if (res.content().readableBytes() == 0) {
            final HttpResponseStatus responseStatus = res.status();
            ByteBufUtil.writeUtf8(res.content(), responseStatus.toString());
            HttpUtil.setContentLength(res, res.content().readableBytes());
        }
        // Send the response and close the connection
        final ChannelFuture future = ctx.writeAndFlush(res);
        future.addListener(ChannelFutureListener.CLOSE);
    }

    public static ByteBuf getPeers(final PeersManager peersManager) {
        try {
            return Unpooled.copiedBuffer(JACKSON_WRITER.writeValueAsString(new PeersStatus(peersManager)), CharsetUtil.UTF_8);
        }
        catch (final JsonProcessingException e) {
            LOG.error("Unable to create peers list:", e);
            return Unpooled.copiedBuffer("{\"error\":\"Unable to create peers list.\"}", CharsetUtil.UTF_8);
        }
    }

    public static ByteBuf getContent(final int networkId, final CompressedPublicKey publicKey) {
        return Unpooled.copiedBuffer(
                "<!DOCTYPE HTML PUBLIC \"-//IETF//DTD HTML 2.0//EN\">\n" +
                        "<html><head>\n" +
                        "<title>400 Bad Request</title>\n" +
                        "</head><body>\n" +
                        "<h1>Bad Request</h1>\n" +
                        "<p>Not a WebSocket Handshake Request: Missing Upgrade.</p>\n" +
                        "<hr>\n" +
                        "<address>drasyl/" + DrasylNode.getVersion() + " with Public Key " + publicKey + " and Network ID " + networkId + "</address>\n" +
                        "</body></html>", CharsetUtil.UTF_8);
    }

    private static class PeersStatus {
        private final Set<CompressedPublicKey> children;
        private final CompressedPublicKey superPeer;

        public PeersStatus(final PeersManager peersManager) {
            this(
                    peersManager.getChildrenKeys(),
                    peersManager.getSuperPeerKey()
            );
        }

        PeersStatus(final Set<CompressedPublicKey> children,
                    final CompressedPublicKey superPeer) {
            this.children = children;
            this.superPeer = superPeer;
        }

        public Set<CompressedPublicKey> getChildren() {
            return children;
        }

        public CompressedPublicKey getSuperPeer() {
            return superPeer;
        }
    }
}