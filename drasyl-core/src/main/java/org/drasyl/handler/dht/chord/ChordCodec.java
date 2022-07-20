package org.drasyl.handler.dht.chord;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import org.drasyl.channel.OverlayAddressedMessage;

import java.util.List;

/**
 * Encodes {@link ChordMessage} messages to {@link ByteBuf}s and vice versa.
 */
@Sharable
public class ChordCodec extends MessageToMessageCodec<OverlayAddressedMessage<ByteBuf>, OverlayAddressedMessage<ChordMessage>> {
    public static final int MAGIC_NUMBER_DUMMY = -256235093;
    // magic number: 4 bytes
    public static final int MIN_MESSAGE_LENGTH = 4;

    @Override
    protected void encode(ChannelHandlerContext channelHandlerContext,
                          OverlayAddressedMessage<ChordMessage> chordMessageOverlayAddressedMessage,
                          List<Object> list) throws Exception {

    }

    @Override
    protected void decode(ChannelHandlerContext channelHandlerContext,
                          OverlayAddressedMessage<ByteBuf> byteBufOverlayAddressedMessage,
                          List<Object> list) throws Exception {

    }
}
