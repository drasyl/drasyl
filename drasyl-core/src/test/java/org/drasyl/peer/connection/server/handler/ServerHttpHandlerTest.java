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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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
    @Mock
    private CompressedPublicKey publicKey;
    @Mock
    private PeersManager peersManager; // Do not remove, it is auto injected into the underTest object
    @InjectMocks
    private ServerHttpHandler underTest;

    @Test
    void shouldPassThroughWebsocketRequests() {
        DefaultHttpHeaders headers = new DefaultHttpHeaders();
        headers.add("upgrade", "websocket");
        DefaultHttpRequest httpRequest = new DefaultFullHttpRequest(HTTP_1_1, GET, "/", Unpooled.buffer(), headers, new DefaultHttpHeaders(true));

        EmbeddedChannel channel = new EmbeddedChannel(underTest);
        channel.writeInbound(httpRequest);

        assertEquals(httpRequest, channel.readInbound());
    }

    @Test
    void shouldReturnNodeInformationOnHeadRequest() {
        DefaultFullHttpRequest httpRequest = new DefaultFullHttpRequest(HTTP_1_1, HEAD, "/");

        EmbeddedChannel channel = new EmbeddedChannel(underTest);
        channel.writeInbound(httpRequest);

        FullHttpResponse httpResponse = channel.readOutbound();

        assertEquals(OK, httpResponse.status());
        assertEquals("drasyl/" + DrasylNode.getVersion(), httpResponse.headers().get("server"));
        assertEquals(publicKey.toString(), httpResponse.headers().get("x-public-key"));

        // Important: release the ByteBuf after testing, otherwise the ResourceLeakDetector raises alarms for tests
        httpResponse.release();
    }

    @Test
    void shouldBlockNonGetMethods() {
        DefaultFullHttpRequest httpRequest = new DefaultFullHttpRequest(HTTP_1_1, POST, "/");

        EmbeddedChannel channel = new EmbeddedChannel(underTest);
        channel.writeInbound(httpRequest);

        FullHttpResponse httpResponse = channel.readOutbound();

        assertEquals(FORBIDDEN, httpResponse.status());

        // Important: release the ByteBuf after testing, otherwise the ResourceLeakDetector raises alarms for tests
        httpResponse.release();
    }

    @Test
    void shouldDisplayMissingUpgradeIndexPage() {
        DefaultFullHttpRequest httpRequest = new DefaultFullHttpRequest(HTTP_1_1, GET, "/");

        EmbeddedChannel channel = new EmbeddedChannel(underTest);
        channel.writeInbound(httpRequest);

        FullHttpResponse httpResponse = channel.readOutbound();
        String content = httpResponse.content().toString(CharsetUtil.UTF_8);

        assertEquals(BAD_REQUEST, httpResponse.status());
        assertThat(content, containsString("Bad Request"));

        // Important: release the ByteBuf after testing, otherwise the ResourceLeakDetector raises alarms for tests
        httpResponse.release();
    }

    @Test
    void shouldDisplayPeersPage() {
        DefaultFullHttpRequest httpRequest = new DefaultFullHttpRequest(HTTP_1_1, GET, "/peers.json");

        EmbeddedChannel channel = new EmbeddedChannel(underTest);
        channel.writeInbound(httpRequest);

        FullHttpResponse httpResponse = channel.readOutbound();
        String content = httpResponse.content().toString(CharsetUtil.UTF_8);

        assertEquals(OK, httpResponse.status());
        assertThatJson(content)
                .isObject()
                .containsKeys("children", "grandchildrenRoutes", "superPeer");

        // Important: release the ByteBuf after testing, otherwise the ResourceLeakDetector raises alarms for tests
        httpResponse.release();
    }

    @Test
    void shouldDisplayNotFoundForAllOtherPaths() {
        DefaultFullHttpRequest httpRequest = new DefaultFullHttpRequest(HTTP_1_1, GET, "/foo/bar.html");

        EmbeddedChannel channel = new EmbeddedChannel(underTest);
        channel.writeInbound(httpRequest);

        FullHttpResponse httpResponse = channel.readOutbound();

        assertEquals(NOT_FOUND, httpResponse.status());

        // Important: release the ByteBuf after testing, otherwise the ResourceLeakDetector raises alarms for tests
        httpResponse.release();
    }
}