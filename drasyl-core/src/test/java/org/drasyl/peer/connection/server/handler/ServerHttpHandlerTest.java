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

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.util.CharsetUtil;
import org.drasyl.DrasylNode;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.peer.PeersManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpMethod.HEAD;
import static io.netty.handler.codec.http.HttpMethod.POST;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class ServerHttpHandlerTest {
    private int networkId;
    @Mock
    private CompressedPublicKey publicKey;
    @Mock
    private PeersManager peersManager;
    private ServerHttpHandler underTest;

    @BeforeEach
    void setUp() {
        underTest = new ServerHttpHandler(networkId, publicKey, peersManager);
    }

    @Test
    void shouldPassThroughWebsocketRequests() {
        final DefaultHttpHeaders headers = new DefaultHttpHeaders();
        headers.add("upgrade", "websocket");
        final DefaultHttpRequest httpRequest = new DefaultFullHttpRequest(HTTP_1_1, GET, "/", Unpooled.buffer(), headers, new DefaultHttpHeaders(true));

        final EmbeddedChannel channel = new EmbeddedChannel(underTest);
        channel.writeInbound(httpRequest);

        assertEquals(httpRequest, channel.readInbound());
    }

    @Test
    void shouldReturnNodeInformationOnHeadRequest() {
        final DefaultFullHttpRequest httpRequest = new DefaultFullHttpRequest(HTTP_1_1, HEAD, "/");

        final EmbeddedChannel channel = new EmbeddedChannel(underTest);
        channel.writeInbound(httpRequest);

        final FullHttpResponse httpResponse = channel.readOutbound();

        assertEquals(OK, httpResponse.status());
        assertEquals("drasyl/" + DrasylNode.getVersion(), httpResponse.headers().get("server"));
        assertEquals(publicKey.toString(), httpResponse.headers().get("x-public-key"));

        // Important: release the ByteBuf after testing, otherwise the ResourceLeakDetector raises alarms for tests
        httpResponse.release();
    }

    @Test
    void shouldBlockNonGetMethods() {
        final DefaultFullHttpRequest httpRequest = new DefaultFullHttpRequest(HTTP_1_1, POST, "/");

        final EmbeddedChannel channel = new EmbeddedChannel(underTest);
        channel.writeInbound(httpRequest);

        final FullHttpResponse httpResponse = channel.readOutbound();

        assertEquals(FORBIDDEN, httpResponse.status());

        // Important: release the ByteBuf after testing, otherwise the ResourceLeakDetector raises alarms for tests
        httpResponse.release();
    }

    @Test
    void shouldDisplayMissingUpgradeIndexPage() {
        final DefaultFullHttpRequest httpRequest = new DefaultFullHttpRequest(HTTP_1_1, GET, "/");

        final EmbeddedChannel channel = new EmbeddedChannel(underTest);
        channel.writeInbound(httpRequest);

        final FullHttpResponse httpResponse = channel.readOutbound();
        final String content = httpResponse.content().toString(CharsetUtil.UTF_8);

        assertEquals(BAD_REQUEST, httpResponse.status());
        assertThat(content, containsString("Bad Request"));

        // Important: release the ByteBuf after testing, otherwise the ResourceLeakDetector raises alarms for tests
        httpResponse.release();
    }

    @Test
    void shouldDisplayPeersPage() {
        final DefaultFullHttpRequest httpRequest = new DefaultFullHttpRequest(HTTP_1_1, GET, "/peers.json");

        final EmbeddedChannel channel = new EmbeddedChannel(underTest);
        channel.writeInbound(httpRequest);

        final FullHttpResponse httpResponse = channel.readOutbound();
        final String content = httpResponse.content().toString(CharsetUtil.UTF_8);

        assertEquals(OK, httpResponse.status());
        assertThatJson(content)
                .isObject()
                .containsKeys("children", "superPeer");

        // Important: release the ByteBuf after testing, otherwise the ResourceLeakDetector raises alarms for tests
        httpResponse.release();
    }

    @Test
    void shouldDisplayNotFoundForAllOtherPaths() {
        final DefaultFullHttpRequest httpRequest = new DefaultFullHttpRequest(HTTP_1_1, GET, "/foo/bar.html");

        final EmbeddedChannel channel = new EmbeddedChannel(underTest);
        channel.writeInbound(httpRequest);

        final FullHttpResponse httpResponse = channel.readOutbound();

        assertEquals(NOT_FOUND, httpResponse.status());

        // Important: release the ByteBuf after testing, otherwise the ResourceLeakDetector raises alarms for tests
        httpResponse.release();
    }
}