/*
 * Copyright (c) 2020-2021 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.cli.handler;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import org.drasyl.util.Worm;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintStream;

import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrintAndExitOnExceptionHandlerTest {
    @Nested
    class ExceptionCaught {
        @Test
        void shouldPrintExceptionAndCloseChannel(@Mock final PrintStream printStream,
                                                 @Mock final Worm<Integer> exitCode,
                                                 @Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                 @Mock final Throwable cause,
                                                 @Mock(answer = RETURNS_DEEP_STUBS) final ChannelFuture channelFuture) {
            when(ctx.channel().isOpen()).thenReturn(true);
            when(ctx.channel().close()).thenReturn(channelFuture);
            when(channelFuture.addListener(any())).then(invocation -> {
                final ChannelFutureListener listener = invocation.getArgument(0, ChannelFutureListener.class);
                listener.operationComplete(channelFuture);
                return channelFuture;
            });
            when(channelFuture.isSuccess()).thenReturn(true);

            final PrintAndExitOnExceptionHandler handler = new PrintAndExitOnExceptionHandler(printStream, exitCode);
            handler.exceptionCaught(ctx, cause);

            verify(cause).printStackTrace(printStream);
            verify(ctx.channel()).close();
            verify(exitCode).trySet(1);
        }
    }
}
