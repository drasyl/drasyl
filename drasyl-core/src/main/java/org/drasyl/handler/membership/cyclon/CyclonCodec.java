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

@Sharable
public class CyclonCodec extends MessageToMessageCodec<OverlayAddressedMessage<ByteBuf>, OverlayAddressedMessage<CyclonMessage>> {
    public static final int MAGIC_NUMBER_REQUEST = -85766231;
    public static final int MAGIC_NUMBER_RESPONSE = -85766230;
    // magic number: 4 bytes
    public static final int MIN_MESSAGE_LENGTH = 4;

    @Override
    protected void encode(final ChannelHandlerContext ctx, final OverlayAddressedMessage<CyclonMessage> msg, final List<Object> out) throws Exception {
        if (msg.content() instanceof CyclonShuffleRequest) {
            final ByteBuf buf = ctx.alloc().buffer();
            buf.writeInt(MAGIC_NUMBER_REQUEST);
            // neighbors
            final Set<CyclonNeighbor> neighbors = msg.content().getNeighbors();
            encodeNeighbors(buf, neighbors);
            out.add(msg.replace(buf));
        }
        else if (msg.content() instanceof CyclonShuffleResponse) {
            final ByteBuf buf = ctx.alloc().buffer();
            buf.writeInt(MAGIC_NUMBER_RESPONSE);
            // neighbors
            final Set<CyclonNeighbor> neighbors = msg.content().getNeighbors();
            encodeNeighbors(buf, neighbors);
            out.add(msg.replace(buf));
        }
        else {
            throw new EncoderException("Unknown CyclonMessage type: " + StringUtil.simpleClassName(msg));
        }
    }

    private void encodeNeighbors(final ByteBuf buf, final Set<CyclonNeighbor> neighbors) {
        for (final CyclonNeighbor neighbor : neighbors) {
            // address
            buf.writeBytes(neighbor.getAddress().toByteArray());
            // size
            buf.writeShort(neighbor.getAge());
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

    /**
     * @throws IllegalArgumentException  if {@code bytes} has wrong key size
     * @throws IndexOutOfBoundsException if {@code buf} does not contain enough bytes
     */
    private Set<CyclonNeighbor> decodeNeighbors(final ByteBuf buf) {
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
