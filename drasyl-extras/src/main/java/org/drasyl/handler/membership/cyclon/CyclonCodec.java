/*
 * Copyright (c) 2020-2022 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.handler.membership.cyclon;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.EncoderException;
import io.netty.handler.codec.MessageToMessageCodec;
import io.netty.util.internal.StringUtil;
import org.drasyl.channel.OverlayAddressedMessage;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.IdentityPublicKey;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.drasyl.identity.IdentityPublicKey.KEY_LENGTH_AS_BYTES;

/**
 * Encodes {@link CyclonMessage} messages to {@link ByteBuf}s and vice versa.
 */
@Sharable
public class CyclonCodec extends MessageToMessageCodec<OverlayAddressedMessage<ByteBuf>, OverlayAddressedMessage<CyclonMessage>> {
    public static final int MAGIC_NUMBER_REQUEST = -85_766_231;
    public static final int MAGIC_NUMBER_RESPONSE = -85_766_230;
    // magic number: 4 bytes
    public static final int MIN_MESSAGE_LENGTH = 4;

    @Override
    protected void encode(final ChannelHandlerContext ctx,
                          final OverlayAddressedMessage<CyclonMessage> msg,
                          final List<Object> out) throws Exception {
        if (msg.content() instanceof CyclonShuffleRequest) {
            final ByteBuf buf = ctx.alloc().buffer();
            buf.writeInt(MAGIC_NUMBER_REQUEST);
            encodeNeighbors(buf, msg.content().getNeighbors());
            out.add(msg.replace(buf));
        }
        else if (msg.content() instanceof CyclonShuffleResponse) {
            final ByteBuf buf = ctx.alloc().buffer();
            buf.writeInt(MAGIC_NUMBER_RESPONSE);
            encodeNeighbors(buf, msg.content().getNeighbors());
            out.add(msg.replace(buf));
        }
        else {
            throw new EncoderException("Unknown CyclonMessage type: " + StringUtil.simpleClassName(msg));
        }
    }

    @Override
    protected void decode(final ChannelHandlerContext ctx,
                          final OverlayAddressedMessage<ByteBuf> msg,
                          final List<Object> out) throws Exception {
        if (msg.content().readableBytes() >= MIN_MESSAGE_LENGTH) {
            msg.content().markReaderIndex();
            final int magicNumber = msg.content().readInt();
            switch (magicNumber) {
                case MAGIC_NUMBER_REQUEST: {
                    final Set<CyclonNeighbor> neighbors = decodeNeighbors(msg.content());
                    out.add(msg.replace(CyclonShuffleRequest.of(neighbors)));
                    break;
                }
                case MAGIC_NUMBER_RESPONSE: {
                    final Set<CyclonNeighbor> neighbors = decodeNeighbors(msg.content());
                    out.add(msg.replace(CyclonShuffleResponse.of(neighbors)));
                    break;
                }
                default: {
                    // wrong magic number -> pass through message
                    msg.content().resetReaderIndex();
                    out.add(msg.retain());
                    break;
                }
            }
        }
        else {
            // too short -> pass through message
            out.add(msg.retain());
        }
    }

    private static void encodeNeighbors(final ByteBuf buf, final Set<CyclonNeighbor> neighbors) {
        for (final CyclonNeighbor neighbor : neighbors) {
            buf.writeBytes(neighbor.getAddress().toByteArray());
            buf.writeShort(neighbor.getAge());
        }
    }

    /**
     * @throws IllegalArgumentException  if {@code bytes} has wrong key size
     * @throws IndexOutOfBoundsException if {@code buf} does not contain enough bytes
     */
    private static Set<CyclonNeighbor> decodeNeighbors(final ByteBuf buf) {
        // neighbors
        final Set<CyclonNeighbor> neighbors = new HashSet<>();
        while (buf.isReadable()) {
            // address
            final byte[] addressBytes = new byte[KEY_LENGTH_AS_BYTES];
            buf.readBytes(addressBytes);
            final DrasylAddress address = IdentityPublicKey.of(addressBytes);
            // age
            final int age = buf.readUnsignedShort();

            final CyclonNeighbor neighbor = CyclonNeighbor.of(address, age);
            neighbors.add(neighbor);
        }

        return neighbors;
    }
}
