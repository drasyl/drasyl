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

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.drasyl.crypto.CryptoException;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.drasyl.peer.connection.message.ChunkedMessage;
import org.drasyl.peer.connection.message.MessageId;
import org.drasyl.peer.connection.message.StatusMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.HashMap;
import java.util.Random;

import static org.drasyl.peer.connection.message.StatusMessage.Code.STATUS_BAD_REQUEST;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChunkedMessageHandlerTest {
    private Identity identity;
    private int maxContentLength;
    private Duration transferTimeout;
    private MessageId msgID;
    @Mock
    private CompressedPublicKey mockedPublicKey;
    @Mock
    private HashMap<MessageId, ChunkedMessageOutput> chunks;
    @Mock
    private ChunkedMessageOutput chunkedMessageOutput;
    @Mock
    private ProofOfWork myProofOfWork;

    @BeforeEach
    void setUp() throws CryptoException {
        identity = Identity.of(ProofOfWork.of(16425882), "030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3", "05880bb5848fc8db0d8f30080b8c923860622a340aae55f4509d62f137707e34");
        maxContentLength = 1024; // 1KB
        transferTimeout = Duration.ofSeconds(10);
        msgID = MessageId.of("89ba3cd9efb7570eb3126d11");
    }

    @Test
    void shouldRelayingChunkedMessage() {
        final ChunkedMessageHandler handler = new ChunkedMessageHandler(chunks, maxContentLength, identity.getPublicKey(), myProofOfWork, transferTimeout);
        final EmbeddedChannel channel = new EmbeddedChannel(handler);

        final ChunkedMessage message = mock(ChunkedMessage.class);
        when(message.getRecipient()).thenReturn(mockedPublicKey);

        assertTrue(channel.writeInbound(message));
        assertEquals(message, channel.readInbound());
        verifyNoInteractions(chunks);
    }

    @Test
    void shouldCreateChunkedMessageOutputOnFirstChunk() {
        final ChunkedMessageHandler handler = new ChunkedMessageHandler(chunks, maxContentLength, identity.getPublicKey(), myProofOfWork, transferTimeout);
        final EmbeddedChannel channel = new EmbeddedChannel(handler);

        final ChunkedMessage message = mock(ChunkedMessage.class);
        when(message.getRecipient()).thenReturn(identity.getPublicKey());
        when(message.getChecksum()).thenReturn("checksum");
        when(message.isInitialChunk()).thenReturn(true);
        when(message.getId()).thenReturn(msgID);

        when(chunks.get(msgID)).thenReturn(chunkedMessageOutput);

        assertFalse(channel.writeInbound(message));
        verify(chunks).put(eq(msgID), isA(ChunkedMessageOutput.class));
        verify(chunks).get(eq(msgID));
        verify(chunkedMessageOutput).addChunk(message);
    }

    @Test
    void shouldAddChunkToChunkedMessageOutput() {
        final ChunkedMessageHandler handler = new ChunkedMessageHandler(chunks, maxContentLength, identity.getPublicKey(), myProofOfWork, transferTimeout);
        final EmbeddedChannel channel = new EmbeddedChannel(handler);

        final ChunkedMessage message = mock(ChunkedMessage.class);
        when(message.getRecipient()).thenReturn(identity.getPublicKey());
        when(message.isInitialChunk()).thenReturn(false);
        when(message.getId()).thenReturn(msgID);

        when(chunks.get(msgID)).thenReturn(chunkedMessageOutput);
        when(chunks.containsKey(msgID)).thenReturn(true);

        assertFalse(channel.writeInbound(message));
        verify(chunks, never()).put(eq(msgID), isA(ChunkedMessageOutput.class));
        verify(chunks).get(eq(msgID));
        verify(chunkedMessageOutput).addChunk(message);
    }

    @Test
    void shouldNotAddChunkIfFirstChunkIsMissing() {
        final ChunkedMessageHandler handler = new ChunkedMessageHandler(chunks, maxContentLength, identity.getPublicKey(), identity.getProofOfWork(), transferTimeout);
        final EmbeddedChannel channel = new EmbeddedChannel(handler);

        final ChunkedMessage message = mock(ChunkedMessage.class);
        when(message.getRecipient()).thenReturn(identity.getPublicKey());
        when(message.isInitialChunk()).thenReturn(false);
        when(message.getId()).thenReturn(msgID);

        when(chunks.containsKey(msgID)).thenReturn(false);

        assertFalse(channel.writeInbound(message));
        verify(chunks, never()).put(eq(msgID), isA(ChunkedMessageOutput.class));
        verify(chunks, never()).get(eq(msgID));
        verify(chunkedMessageOutput, never()).addChunk(message);

        assertEquals(new StatusMessage(identity.getPublicKey(), identity.getProofOfWork(), STATUS_BAD_REQUEST, msgID), channel.readOutbound());
    }

    @Test
    void shouldDropToBigMessage() {
        final byte[] payload = new byte[maxContentLength + 1];
        new Random().nextBytes(payload);

        final ChunkedMessageHandler handler = new ChunkedMessageHandler(maxContentLength, identity.getPublicKey(), myProofOfWork, transferTimeout);
        final EmbeddedChannel channel = new EmbeddedChannel(handler);

        final ApplicationMessage message = mock(ApplicationMessage.class);
        when(message.getPayload()).thenReturn(payload);
        when(message.getId()).thenReturn(msgID);

        assertThrows(IllegalArgumentException.class, () -> channel.writeOutbound(message));
        assertNull(channel.readOutbound());
    }

    @Test
    void shouldSkipMessageWithWrongPayloadSize() {
        final byte[] payload = new byte[maxContentLength];
        new Random().nextBytes(payload);

        final ChunkedMessageHandler handler = new ChunkedMessageHandler(maxContentLength, identity.getPublicKey(), myProofOfWork, transferTimeout);
        final EmbeddedChannel channel = new EmbeddedChannel(handler);

        final ApplicationMessage message = mock(ApplicationMessage.class);
        when(message.getPayload()).thenReturn(payload);

        assertTrue(channel.writeOutbound(message));
        assertEquals(message, channel.readOutbound());
    }

    @Test
    void shouldSplittMessageWithPayloadGreaterThanChunkSize() {
        final byte[] payload = new byte[ChunkedMessageHandler.CHUNK_SIZE + 1];
        new Random().nextBytes(payload);

        final ChunkedMessageHandler handler = new ChunkedMessageHandler(payload.length + 1, identity.getPublicKey(), myProofOfWork, transferTimeout);
        final EmbeddedChannel channel = new EmbeddedChannel(handler);

        final ApplicationMessage message = mock(ApplicationMessage.class);
        when(message.getPayload()).thenReturn(payload);
        when(message.payloadAsByteBuf()).thenReturn(Unpooled.wrappedBuffer(payload));

        assertTrue(channel.writeOutbound(message));
        assertThat(channel.readOutbound(), instanceOf(ChunkedMessageInput.class));
    }
}