package org.drasyl.example.cyclon;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageCodec;
import org.drasyl.channel.OverlayAddressedMessage;

import java.util.List;

public class CyclonCodec extends ByteToMessageCodec<OverlayAddressedMessage<CyclonMessage>> {


    @Override
    protected void encode(final ChannelHandlerContext ctx,
                          final OverlayAddressedMessage<CyclonMessage> msg,
                          final ByteBuf out) throws Exception {

    }

    @Override
    protected void decode(final ChannelHandlerContext ctx,
                          final ByteBuf in,
                          final List<Object> out) throws Exception {

    }
}
