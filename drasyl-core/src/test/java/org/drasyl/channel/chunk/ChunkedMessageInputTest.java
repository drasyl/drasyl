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
package org.drasyl.channel.chunk;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.stream.ChunkedFile;
import io.netty.handler.stream.ChunkedInput;
import io.netty.handler.stream.ChunkedNioFile;
import io.netty.handler.stream.ChunkedNioStream;
import io.netty.handler.stream.ChunkedStream;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.internal.PlatformDependent;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkedMessageInputTest {
    private static final byte[] BYTES = new byte[1024 * 64];
    private static final File TMP;

    static {
        for (int i = 0; i < BYTES.length; i++) {
            BYTES[i] = (byte) i;
        }

        FileOutputStream out = null;
        try {
            TMP = PlatformDependent.createTempFile("netty-chunk-", ".tmp", null);
            TMP.deleteOnExit();
            out = new FileOutputStream(TMP);
            out.write(BYTES);
            out.flush();
        }
        catch (final IOException e) {
            throw new RuntimeException(e);
        }
        finally {
            if (out != null) {
                try {
                    out.close();
                }
                catch (final IOException e) {
                    // ignore
                }
            }
        }
    }

    @Test
    void testChunkedStream() {
        check(new ChunkedMessageInput(new ChunkedStream(new ByteArrayInputStream(BYTES), 850)));
    }

    @Test
    void testChunkedNioStream() {
        check(new ChunkedMessageInput(new ChunkedNioStream(Channels.newChannel(new ByteArrayInputStream(BYTES)))));
    }

    @Test
    void testChunkedFile() throws IOException {
        check(new ChunkedMessageInput(new ChunkedFile(TMP)));
    }

    @Test
    void testTooManyChunks() {
        final ChunkedMessageInput input = new ChunkedMessageInput(new ChunkedStream(new ByteArrayInputStream(BYTES), 100));
        final EmbeddedChannel channel = new EmbeddedChannel(new ChunkedWriteHandler());

        assertThrows(IOException.class, () -> {
            for (int i = 0; i < 257; i++) {
                channel.writeOutbound(input);
            }
        });
    }

    @Test
    void testChunkedNioFile() throws IOException {
        check(new ChunkedMessageInput(new ChunkedNioFile(TMP)));
    }

    private static void check(final ChunkedInput<?>... inputs) {
        final EmbeddedChannel ch = new EmbeddedChannel(new ChunkedWriteHandler());

        for (final ChunkedInput<?> input : inputs) {
            ch.writeOutbound(input);
        }

        assertTrue(ch.finish());

        int i = 0;
        int read = 0;
        byte chunkNo = 0;
        MessageChunk lastChunk = null;
        while (true) {
            final MessageChunk chunk = ch.readOutbound();
            if (chunk == null) {
                break;
            }

            if (lastChunk != null) {
                assertThat(lastChunk, instanceOf(MessageChunk.class));

                assertEquals(chunkNo++, lastChunk.chunkNo());
            }

            final ByteBuf buffer = chunk.content();
            while (buffer.isReadable()) {
                assertEquals(BYTES[i++], buffer.readByte());
                read++;
                if (i == BYTES.length) {
                    i = 0;
                }
            }
            buffer.release();

            // Save last chunk
            lastChunk = chunk;
        }

        assertEquals(BYTES.length * inputs.length, read);
        assertThat(lastChunk, instanceOf(LastMessageChunk.class));
        assertEquals(chunkNo, lastChunk.chunkNo());
    }
}
