package org.drasyl.peer.connection.server.handler;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import org.drasyl.DrasylNode;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.peer.PeersManager;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static io.netty.handler.codec.http.HttpMethod.HEAD;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServerHttpHandlerTest {
    @Mock
    private CompressedPublicKey publicKey;
    @Mock
    private PeersManager peersManager;
    @InjectMocks
    private ServerHttpHandler underTest;

    @Test
    @Disabled
    void shouldReturnNodeInformationOnHeadRequest(@Mock(answer = Answers.RETURNS_DEEP_STUBS) FullHttpRequest httpRequest) {
        when(httpRequest.method()).thenReturn(HEAD);

        EmbeddedChannel channel = new EmbeddedChannel(underTest);
        channel.writeInbound(httpRequest);

        FullHttpResponse httpResponse = channel.readOutbound();

        assertEquals(OK, httpResponse.status());
        assertEquals(DrasylNode.getVersion(), httpResponse.headers().get("server"));
        assertEquals(publicKey, httpResponse.headers().get("x-public-key"));
    }
}