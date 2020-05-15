package org.drasyl.core.common.handler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseDecoder;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import org.drasyl.core.common.message.QuitMessage;
import org.drasyl.core.common.message.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class WebSocketClientHandlerTest {
    private WebSocketClientHandshaker handshaker;
    private CompletableFuture<Void> handshakeFuture;
    private ChannelHandlerContext ctx;
    private Message quitMessage;
    private Throwable cause;
    private FullHttpResponse fullHttpResponse;
    private CloseWebSocketFrame closeWebSocketFrame;
    private Channel channel;
    private ByteBuf byteBuf;
    private ChannelId channelId;
    private WebSocketFrame webSocketFrame;
    private ChannelPipeline channelPipeline;
    private ChannelPromise promise;

    @BeforeEach
    void setUp() {
        handshaker = mock(WebSocketClientHandshaker.class);
        handshakeFuture = mock(CompletableFuture.class);
        ctx = mock(ChannelHandlerContext.class);
        quitMessage = mock(QuitMessage.class);
        fullHttpResponse = mock(FullHttpResponse.class);
        closeWebSocketFrame = mock(CloseWebSocketFrame.class);
        cause = mock(Throwable.class);
        channel = mock(Channel.class);
        byteBuf = mock(ByteBuf.class);
        channelId = mock(ChannelId.class);
        webSocketFrame = mock(WebSocketFrame.class);
        channelPipeline = mock(ChannelPipeline.class);
        promise = mock(ChannelPromise.class);
    }

    @Test
    void channelWrite0ShouldBlockUntilHandshakeIsDone() throws Exception {
        WebSocketClientHandler handler = new WebSocketClientHandler(handshaker, handshakeFuture);

        handler.channelWrite0(ctx, quitMessage, promise);

        verify(handshakeFuture).join();
    }

    @Test
    void channelRead0ShouldCompleteHandshakeIfHandshakeIsNotCompleteAndHttpMessageReceived() {
        when(ctx.channel()).thenReturn(channel);
        when(handshaker.isHandshakeComplete()).thenReturn(false);
        when(fullHttpResponse.headers()).thenReturn(mock(HttpHeaders.class));
        when(channel.pipeline()).thenReturn(channelPipeline);
        when(channel.eventLoop()).thenReturn(mock(EventLoop.class));
        when(channel.id()).thenReturn(channelId);
        when(channelPipeline.context(HttpResponseDecoder.class)).thenReturn(mock(ChannelHandlerContext.class));

        WebSocketClientHandler handler = new WebSocketClientHandler(handshaker, handshakeFuture);
        handler.channelRead0(ctx, fullHttpResponse);

        verify(handshaker).finishHandshake(channel, fullHttpResponse);
        verify(handshakeFuture).complete(null);
    }

    @Test
    void channelRead0ShouldThrowExceptionIfHandshakeIsCompleteAndHttpMessageReceived() {
        when(ctx.channel()).thenReturn(channel);
        when(handshaker.isHandshakeComplete()).thenReturn(true);
        when(fullHttpResponse.content()).thenReturn(byteBuf);

        WebSocketClientHandler handler = new WebSocketClientHandler(handshaker, handshakeFuture);
        assertThrows(IllegalStateException.class, () -> handler.channelRead0(ctx, fullHttpResponse));
    }

    @Test
    void channelRead0ShouldCloseWebsocketIfHandshakeIsCompleteAndCloseWebSocketFrameMessageReceived() {
        when(ctx.channel()).thenReturn(channel);
        when(channel.id()).thenReturn(channelId);
        when(handshaker.isHandshakeComplete()).thenReturn(true);

        WebSocketClientHandler handler = new WebSocketClientHandler(handshaker, handshakeFuture);
        handler.channelRead0(ctx, closeWebSocketFrame);

        verify(channel).close();
    }

    @Test
    void channelRead0ShouldPassThroughAllOtherWebSocketFrameMessagesIfHandshakeIsComplete() {
        when(handshaker.isHandshakeComplete()).thenReturn(true);
        when(webSocketFrame.retain()).thenReturn(webSocketFrame);

        WebSocketClientHandler handler = new WebSocketClientHandler(handshaker, handshakeFuture);
        handler.channelRead0(ctx, webSocketFrame);

        verify(ctx).fireChannelRead(webSocketFrame);
        verify(webSocketFrame).retain();
    }

    @Test
    void exceptionCaughtShouldAbortHandshakeAndCloseChannel() {
        when(handshakeFuture.isDone()).thenReturn(false);

        WebSocketClientHandler handler = new WebSocketClientHandler(handshaker, handshakeFuture);
        handler.exceptionCaught(ctx, cause);

        verify(handshakeFuture).completeExceptionally(any());
        verify(ctx).close();
    }
}