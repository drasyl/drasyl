package org.drasyl.handler.dht.chord;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.EncoderException;
import io.netty.handler.codec.MessageToMessageCodec;
import io.netty.util.internal.StringUtil;
import org.drasyl.channel.OverlayAddressedMessage;

import java.util.List;

/**
 * Encodes {@link ChordMessage} messages to {@link ByteBuf}s and vice versa.
 */
@Sharable
public class ChordCodec extends MessageToMessageCodec<OverlayAddressedMessage<ByteBuf>, OverlayAddressedMessage<ChordMessage>> {
    public static final int MAGIC_NUMBER_FIND_SUCCESSOR = -256235093;
    // magic number: 4 bytes
    public static final int MIN_MESSAGE_LENGTH = 4;

    @Override
    protected void encode(final ChannelHandlerContext ctx,
                          final OverlayAddressedMessage<ChordMessage> msg,
                          final List<Object> out) throws Exception {
        if (msg.content() instanceof FindSuccessor) {
            final ByteBuf buf = ctx.alloc().buffer();
            buf.writeInt(MAGIC_NUMBER_FIND_SUCCESSOR);
            buf.writeLong(((FindSuccessor) msg.content()).getId());
            out.add(msg.replace(buf));
        }
        else {
            throw new EncoderException("Unknown ChordMessage type: " + StringUtil.simpleClassName(msg));
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
                case MAGIC_NUMBER_FIND_SUCCESSOR: {
                    out.add(msg.replace(FindSuccessor.of(msg.content().readLong())));
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
}
