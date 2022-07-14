package org.drasyl.handler.membership.cyclon;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.EncoderException;
import io.netty.handler.codec.MessageToMessageCodec;
import io.netty.util.internal.StringUtil;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.IdentityPublicKey;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.drasyl.identity.IdentityPublicKey.KEY_LENGTH_AS_BYTES;

public class CyclonCodec extends MessageToMessageCodec<ByteBuf, CyclonMessage> {
    public static final int MAGIC_NUMBER_REQUEST = -85766231;
    public static final int MAGIC_NUMBER_RESPONSE = -85766230;
    // magic number: 4 bytes
    // view: capacity + size: 4 bytes
    public static final int MIN_MESSAGE_LENGTH = 8;

    @Override
    protected void encode(final ChannelHandlerContext ctx, final CyclonMessage msg, final List<Object> out) throws Exception {
        if (msg instanceof CyclonShuffleRequest) {
            final ByteBuf buf = ctx.alloc().buffer();
            buf.writeInt(MAGIC_NUMBER_REQUEST);
            // view
            final CyclonView view = msg.getView();
            encodeView(buf, view);
            out.add(buf);
        }
        else if (msg instanceof CyclonShuffleResponse) {
            final ByteBuf buf = ctx.alloc().buffer();
            buf.writeInt(MAGIC_NUMBER_RESPONSE);
            // view
            final CyclonView view = msg.getView();
            encodeView(buf, view);
            out.add(buf);
        }
        else {
            throw new EncoderException("Unknown CyclonMessage type: " + StringUtil.simpleClassName(msg));
        }
    }

    private void encodeView(final ByteBuf buf, final CyclonView view) {
        // capacity
        buf.writeShort(view.getCapacity());
        // size
        buf.writeShort(view.getNeighbors().size());
        // neighbors
        for (final CyclonNeighbor neighbor : view.getNeighbors()) {
            // address
            buf.writeBytes(neighbor.getAddress().toByteArray());
            // size
            buf.writeShort(neighbor.getAge());
        }
    }

    @Override
    protected void decode(final ChannelHandlerContext ctx,
                          final ByteBuf in,
                          final List<Object> out) throws Exception {
        if (in.readableBytes() >= MIN_MESSAGE_LENGTH) {
            in.markReaderIndex();
            final int magicNumber = in.readInt();
            switch (magicNumber) {
                case MAGIC_NUMBER_REQUEST: {
                    final CyclonView view = decodeView(in);
                    out.add(CyclonShuffleRequest.of(view));
                    break;
                }
                case MAGIC_NUMBER_RESPONSE: {
                    final CyclonView view = decodeView(in);
                    out.add(CyclonShuffleResponse.of(view));
                    break;
                }
                default: {
                    // wrong magic number -> pass through message
                    in.resetReaderIndex();
                    out.add(in.retain());
                    break;
                }
            }
        }
        else {
            // too short -> pass through message
            out.add(in.retain());
        }
    }

    /**
     * @throws IllegalArgumentException if {@code bytes} has wrong key size
     * @throws IndexOutOfBoundsException if {@code buf} does not contain enough bytes
     */
    private CyclonView decodeView(final ByteBuf buf) {
        // capacity
        final int capacity = buf.readUnsignedShort();
        // size
        final int size = buf.readUnsignedShort();
        // neighbors
        final Set<CyclonNeighbor> neighbors = new HashSet<>();
        for (int i = 0; i < size; i++) {
            // address
            final byte[] addressBytes = new byte[KEY_LENGTH_AS_BYTES];
            buf.readBytes(addressBytes);
            final DrasylAddress address = IdentityPublicKey.of(addressBytes);
            // age
            final int age = buf.readUnsignedShort();

            final CyclonNeighbor neighbor = CyclonNeighbor.of(address, age);
            neighbors.add(neighbor);
        }

        return CyclonView.of(capacity, neighbors);
    }
}
