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

package org.drasyl.core.common.handler;

import org.drasyl.core.common.handler.codec.message.MessageDecoder;
import org.drasyl.core.common.handler.codec.message.MessageEncoder;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.flush.FlushConsolidationHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.IdleStateHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.time.Duration;

import static org.mockito.Mockito.*;

class DefaultSessionInitializerTest {
    private DefaultSessionInitializer classUnderTest;
    private ChannelPipeline pipeline;
    private SslHandler sslHandler;
    private SocketChannel ch;

    @BeforeEach
    void setUp() throws IllegalAccessException, NoSuchFieldException {
        classUnderTest = mock(DefaultSessionInitializer.class, Mockito.CALLS_REAL_METHODS);
        pipeline = mock(ChannelPipeline.class);
        sslHandler = mock(SslHandler.class);
        ch = mock(SocketChannel.class);

        Field field = DefaultSessionInitializer.class.getDeclaredField("readIdleTimeout");
        field.setAccessible(true);
        field.set(classUnderTest, Duration.ZERO);

        when(ch.pipeline()).thenReturn(pipeline);
        when(classUnderTest.generateSslContext(ch)).thenReturn(sslHandler);
    }

    // should call all stages of the pipeline
    @Test
    void initChannel() {
        classUnderTest.initChannel(ch);

        verify(classUnderTest).beforeSslStage(ch);
        verify(classUnderTest).sslStage(ch);
        verify(classUnderTest).afterSslStage(ch);

        verify(classUnderTest).beforeBufferStage(pipeline);
        verify(classUnderTest).bufferStage(pipeline);
        verify(classUnderTest).afterBufferStage(pipeline);

        verify(classUnderTest).beforeMarshalStage(pipeline);
        verify(classUnderTest).marshalStage(pipeline);
        verify(classUnderTest).afterMarshalStage(pipeline);

        verify(classUnderTest).beforeFilterStage(pipeline);
        verify(classUnderTest).filterStage(pipeline);
        verify(classUnderTest).afterFilterStage(pipeline);

        verify(classUnderTest).beforePojoMarshalStage(pipeline);
        verify(classUnderTest).pojoMarshalStage(pipeline);
        verify(classUnderTest).afterPojoMarshalStage(pipeline);

        verify(classUnderTest).beforeIdleStage(pipeline);
        verify(classUnderTest).idleStage(pipeline);
        verify(classUnderTest).afterIdleStage(pipeline);

        verify(classUnderTest).customStage(pipeline);

        verify(classUnderTest).beforeExceptionStage(pipeline);
        verify(classUnderTest).exceptionStage(pipeline);
        verify(classUnderTest).afterExceptionStage(pipeline);
    }

    @Test
    void testSslStage() {
        classUnderTest.sslStage(ch);

        verify(pipeline).addLast("sslHandler", sslHandler);
    }

    @Test
    void testSslStageNull() {
        when(classUnderTest.generateSslContext(ch)).thenReturn(null);
        classUnderTest.sslStage(ch);

        verify(pipeline, never()).addLast(sslHandler);
    }

    @Test
    void testBufferStage() {
        classUnderTest.bufferStage(pipeline);

        verify(pipeline).addLast(any(FlushConsolidationHandler.class));
    }

    @Test
    void testPojoMarshalStage() {
        classUnderTest.pojoMarshalStage(pipeline);

        verify(pipeline).addLast(eq("messageDecoder"), any(MessageDecoder.class));
        verify(pipeline).addLast(eq("messageEncoder"), any(MessageEncoder.class));
    }

    @Test
    void testIdleStage() {
        DefaultSessionInitializer classUnderTest = new DefaultSessionInitializer(1, Duration.ofMillis(1L), (short) 1) {
            @Override
            protected SslHandler generateSslContext(SocketChannel ch) {
                return null;
            }

            @Override
            protected void customStage(ChannelPipeline pipeline) {

            }

            @Override
            protected void beforeMarshalStage(ChannelPipeline pipeline) {

            }
        };

        classUnderTest.idleStage(pipeline);

        verify(pipeline).addLast(eq("idleEvent"), any(IdleStateHandler.class));
        verify(pipeline).addLast(eq("pingPongHandler"), any(PingPongHandler.class));
    }

    @Test
    void testIdleStageDisabled() {
        classUnderTest.idleStage(pipeline);

        verify(pipeline, never()).addLast(eq("idleEvent"), any(IdleStateHandler.class));
        verify(pipeline).addLast(eq("pingPongHandler"), any(PingPongHandler.class));
    }
}