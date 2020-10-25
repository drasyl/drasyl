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
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.drasyl.peer.connection.message.ChunkedMessage;
import org.drasyl.peer.connection.message.MessageId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChunkedMessageInputTest {
    @Mock
    private CompressedPublicKey sender;
    @Mock
    private ProofOfWork proofOfWork;
    @Mock
    private CompressedPublicKey recipient;
    @Mock
    private Queue<ByteBuf> chunks;
    @Mock
    private ByteBuf sourcePayload;
    private MessageId msgID;
    private long progress;
    private boolean sentLastChunk;
    private byte[] payload;
    private int chunkSize;
    private int contentLength;
    private String checksum;

    @BeforeEach
    void setUp() {
        msgID = MessageId.of("89ba3cd9efb7570eb3126d11");
        payload = new byte[]{};
        sentLastChunk = false;
    }

    @Test
    void shouldConstructChunksFromEvenPayloadSize() {
        payload = new byte[]{
                63,
                -38,
                -22,
                -39
        };
        chunkSize = 1;
        contentLength = 4;
        checksum = "5a93d52bc11ab74c7057c5690f9381a3";

        final ChunkedMessageInput input = new ChunkedMessageInput(sender, proofOfWork, recipient, contentLength, checksum, chunks, sourcePayload, msgID, progress, sentLastChunk);

        input.chunkedArray(chunks, Unpooled.wrappedBuffer(payload), chunkSize);
        verify(chunks, times(4)).add(isA(ByteBuf.class));
        verify(chunks).add(eq(Unpooled.wrappedBuffer(new byte[]{ 63 })));
        verify(chunks).add(eq(Unpooled.wrappedBuffer(new byte[]{ -38 })));
        verify(chunks).add(eq(Unpooled.wrappedBuffer(new byte[]{ -22 })));
        verify(chunks).add(eq(Unpooled.wrappedBuffer(new byte[]{ -39 })));
    }

    @Test
    void shouldConstructChunksFromOddPayloadSize() {
        payload = new byte[]{
                63,
                -38,
                -22,
                -39,
                1
        };
        chunkSize = 2;
        contentLength = 5;
        checksum = "5d6d29bd1a2d27159acb9447042cd997";

        final ChunkedMessageInput input = new ChunkedMessageInput(sender, proofOfWork, recipient, contentLength, checksum, chunks, sourcePayload, msgID, progress, sentLastChunk);

        input.chunkedArray(chunks, Unpooled.wrappedBuffer(payload), chunkSize);
        verify(chunks, times(3)).add(isA(ByteBuf.class));
        verify(chunks).add(eq(Unpooled.wrappedBuffer(new byte[]{ 63, -38 })));
        verify(chunks).add(eq(Unpooled.wrappedBuffer(new byte[]{ -22, -39 })));
        verify(chunks).add(eq(Unpooled.wrappedBuffer(new byte[]{ 1 })));
    }

    @Test
    void shouldProduceFirstChunkedMessage() {
        final ByteBuf chunk = Unpooled.wrappedBuffer(new byte[]{ 63, -38 });
        when(chunks.poll()).thenReturn(chunk);
        chunkSize = 1;
        contentLength = 2;
        checksum = "5a93d52bc11ab74c7057c5690f9381a3";

        final ChunkedMessageInput input = new ChunkedMessageInput(sender, proofOfWork, recipient, contentLength, checksum, chunks, sourcePayload, msgID, progress, sentLastChunk);

        final ChunkedMessage expectedChunk = ChunkedMessage.createFirstChunk(msgID, sender, proofOfWork, recipient, chunk.array(), contentLength, checksum);
        assertEquals(expectedChunk, input.readChunk(mock(ByteBufAllocator.class)));
        assertEquals(progress + contentLength, input.progress());
        assertEquals(contentLength, input.length());
        assertFalse(input.isEndOfInput());
    }

    @Test
    void shouldProduceFollowingChunkedMessage() {
        final ByteBuf chunk = Unpooled.wrappedBuffer(new byte[]{ 63, -38 });
        when(chunks.poll()).thenReturn(chunk);
        chunkSize = 1;
        contentLength = 2;
        checksum = "5a93d52bc11ab74c7057c5690f9381a3";
        progress = 1;

        final ChunkedMessageInput input = new ChunkedMessageInput(sender, proofOfWork, recipient, contentLength, checksum, chunks, sourcePayload, msgID, progress, sentLastChunk);

        final ChunkedMessage expectedChunk = ChunkedMessage.createFollowChunk(msgID, sender, proofOfWork, recipient, chunk.array());
        assertEquals(expectedChunk, input.readChunk(mock(ByteBufAllocator.class)));
        assertEquals(progress + contentLength, input.progress());
        assertEquals(contentLength, input.length());
        assertFalse(input.isEndOfInput());
    }

    @Test
    void shouldProduceLastChunkedMessage() {
        when(chunks.isEmpty()).thenReturn(true);
        chunkSize = 1;
        contentLength = 2;
        checksum = "5a93d52bc11ab74c7057c5690f9381a3";
        progress = 2;

        final ChunkedMessageInput input = new ChunkedMessageInput(sender, proofOfWork, recipient, contentLength, checksum, chunks, sourcePayload, msgID, progress, sentLastChunk);

        final ChunkedMessage expectedChunk = ChunkedMessage.createLastChunk(msgID, sender, proofOfWork, recipient);
        assertEquals(expectedChunk, input.readChunk(mock(ByteBufAllocator.class)));
        assertEquals(progress, input.progress());
        assertEquals(contentLength, input.length());
        assertTrue(input.isEndOfInput());
    }

    @Test
    void closeShouldClearPayloadAndChunks() {
        final ChunkedMessageInput input = new ChunkedMessageInput(sender, proofOfWork, recipient, contentLength, checksum, chunks, sourcePayload, msgID, progress, sentLastChunk);

        input.close();

        verify(chunks).clear();
        verify(sourcePayload).release();
    }

    @Test
    void shouldCreateFromApplicationMessage() {
        final ApplicationMessage message = mock(ApplicationMessage.class);
        when(message.getSender()).thenReturn(sender);
        when(message.getRecipient()).thenReturn(recipient);
        when(message.payloadAsByteBuf()).thenReturn(sourcePayload);
        when(message.getPayload()).thenReturn(payload);
        when(message.getId()).thenReturn(msgID);

        new ChunkedMessageInput(message, chunkSize);

        verify(message).getSender();
        verify(message).getRecipient();
        verify(message, times(2)).payloadAsByteBuf();
        verify(message).getPayload();
        verify(message).getId();
    }
}
