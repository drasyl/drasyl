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
package org.drasyl.peer.connection.handler.stream;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.EventExecutor;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.drasyl.peer.connection.message.ChunkedMessage;
import org.drasyl.peer.connection.message.MessageId;
import org.drasyl.peer.connection.message.StatusMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.TimeUnit;

import static org.drasyl.peer.connection.message.StatusMessage.Code.STATUS_PAYLOAD_TOO_LARGE;
import static org.drasyl.peer.connection.message.StatusMessage.Code.STATUS_PRECONDITION_FAILED;
import static org.drasyl.peer.connection.message.StatusMessage.Code.STATUS_REQUEST_TIMEOUT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChunkedMessageOutputTest {
    @Mock
    ChunkedMessage chunk;
    @Mock
    private CompressedPublicKey sender;
    @Mock
    private ProofOfWork proofOfWork;
    @Mock
    private CompressedPublicKey recipient;
    @Mock
    private ChannelHandlerContext ctx;
    @Mock
    private Runnable removeAction;
    @Mock
    private ByteBuf payload;
    private int contentLength;
    private int maxContentLength;
    private String checksum;
    private MessageId msgID;
    private int progress;
    private byte[] rawPayload;
    @Mock
    private CompressedPublicKey myPublicKey;
    @Mock
    private ProofOfWork myProofOfWork;

    @BeforeEach
    void setUp() {
        msgID = MessageId.of("89ba3cd9efb7570eb3126d11");
        rawPayload = new byte[]{
                63,
                -38,
                -22,
                -39
        };
        contentLength = 4;
        checksum = "5a93d52bc11ab74c7057c5690f9381a3";
    }

    @Test
    void shouldRaiseErrorOnTooBigPayload() {
        progress = 4;
        maxContentLength = 4;
        final ChunkedMessageOutput output = new ChunkedMessageOutput(myPublicKey, myProofOfWork, ctx, sender, proofOfWork, recipient, contentLength, checksum, msgID, maxContentLength, payload, progress, removeAction);

        when(chunk.getPayload()).thenReturn(rawPayload);
        output.addChunk(chunk);

        verify(payload).release();
        verify(removeAction).run();
        verify(ctx).writeAndFlush(eq(new StatusMessage(sender, proofOfWork, STATUS_PAYLOAD_TOO_LARGE, msgID)));
    }

    @Test
    void shouldAddChunk() {
        maxContentLength = 10;

        final ChunkedMessageOutput output = new ChunkedMessageOutput(myPublicKey, myProofOfWork, ctx, sender, proofOfWork, recipient, contentLength, checksum, msgID, maxContentLength, payload, progress, removeAction);
        when(chunk.getPayload()).thenReturn(rawPayload);
        when(chunk.payloadAsByteBuf()).thenReturn(Unpooled.wrappedBuffer(rawPayload));

        output.addChunk(chunk);
        verify(payload).writeBytes(chunk.payloadAsByteBuf(), 0, 4);
    }

    @Test
    void shouldRaiseErrorOnFalseChecksum() {
        rawPayload = new byte[]{};
        when(payload.array()).thenReturn(rawPayload);
        checksum = "abc";

        final ChunkedMessageOutput output = new ChunkedMessageOutput(myPublicKey, myProofOfWork, ctx, sender, proofOfWork, recipient, contentLength, checksum, msgID, maxContentLength, payload, progress, removeAction);
        when(chunk.getPayload()).thenReturn(rawPayload);
        when(chunk.payloadAsByteBuf()).thenReturn(Unpooled.wrappedBuffer(rawPayload));

        output.addChunk(chunk);
        verify(payload, never()).writeBytes(eq(chunk.payloadAsByteBuf()), anyInt(), anyInt());
        verify(ctx).writeAndFlush(new StatusMessage(sender, proofOfWork, STATUS_PRECONDITION_FAILED, msgID));
    }

    @Test
    void shouldPassOnApplicationMessage() {
        maxContentLength = 10;

        when(payload.array()).thenReturn(rawPayload);

        final ChunkedMessageOutput output = new ChunkedMessageOutput(myPublicKey, myProofOfWork, ctx, sender, proofOfWork, recipient, contentLength, checksum, msgID, maxContentLength, payload, progress, removeAction);
        when(chunk.getPayload()).thenReturn(new byte[]{});
        when(chunk.payloadAsByteBuf()).thenReturn(Unpooled.buffer());

        output.addChunk(chunk);
        verify(payload, never()).writeBytes(eq(chunk.payloadAsByteBuf()), anyInt(), anyInt());
        verify(ctx).fireChannelRead(eq(new ApplicationMessage(msgID, sender, proofOfWork, recipient, payload.array(), (short) 0)));
        verify(payload).release();
        verify(removeAction).run();
    }

    @Test
    void shouldReleaseOnTimeout() {
        final EventExecutor eventExecutor = mock(EventExecutor.class);
        when(ctx.executor()).thenReturn(eventExecutor);
        final ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);

        new ChunkedMessageOutput(myPublicKey, myProofOfWork, ctx, sender, proofOfWork, recipient, contentLength, checksum, msgID, maxContentLength, removeAction, 1L);

        verify(eventExecutor).schedule(captor.capture(), eq(1L), eq(TimeUnit.MILLISECONDS));
        captor.getValue().run();
        verify(ctx).writeAndFlush(new StatusMessage(sender, proofOfWork, STATUS_REQUEST_TIMEOUT, msgID));
        verify(removeAction).run();
    }

    @Test
    void equalsAndHashCodeTest() {
        final ChunkedMessageOutput output1 = new ChunkedMessageOutput(myPublicKey, myProofOfWork, ctx, sender, proofOfWork, recipient, contentLength, checksum, msgID, maxContentLength, payload, progress, removeAction);
        final ChunkedMessageOutput output2 = new ChunkedMessageOutput(myPublicKey, myProofOfWork, ctx, sender, proofOfWork, recipient, contentLength, checksum, msgID, maxContentLength, payload, progress, removeAction);
        final ChunkedMessageOutput output3 = new ChunkedMessageOutput(myPublicKey, myProofOfWork, ctx, sender, proofOfWork, recipient, contentLength, checksum, MessageId.of("412176952b5b81fd13f84a7c"), maxContentLength, payload, progress, removeAction);

        assertEquals(output1, output2);
        assertEquals(output1.hashCode(), output2.hashCode());
        assertNotEquals(output1, output3);
        assertNotEquals(output1.hashCode(), output3.hashCode());
    }
}