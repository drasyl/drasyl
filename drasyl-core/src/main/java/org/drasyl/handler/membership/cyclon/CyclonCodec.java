package org.drasyl.handler.membership.cyclon;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageCodec;
import io.netty.handler.codec.EncoderException;
import io.netty.handler.codec.MessageToMessageCodec;
import io.netty.util.internal.StringUtil;
import org.drasyl.handler.arq.gobackn.GoBackNArqAck;
import org.drasyl.handler.arq.gobackn.GoBackNArqData;
import org.drasyl.handler.arq.gobackn.GoBackNArqFirstData;
import org.drasyl.handler.arq.gobackn.GoBackNArqLastData;
import org.drasyl.handler.arq.gobackn.GoBackNArqRst;
import org.drasyl.util.UnsignedInteger;

import java.util.List;

public class CyclonCodec extends MessageToMessageCodec<ByteBuf, CyclonMessage> {
    public static final int MAGIC_NUMBER_REQUEST = -85766231;
    public static final int MAGIC_NUMBER_RESPONSE = -85766230;
    // magic number: 4 bytes
    // xxxx
    public static final int MIN_MESSAGE_LENGTH = 4;

    @Override
    protected void encode(final ChannelHandlerContext ctx, final CyclonMessage msg, final List<Object> out) throws Exception {
        if (msg instanceof CyclonShuffleRequest) {
            final ByteBuf buf = ctx.alloc().buffer();
            buf.writeInt(MAGIC_NUMBER_REQUEST);
            // FIXME: implement
            out.add(buf);
        }
        else if (msg instanceof CyclonShuffleResponse) {
            final ByteBuf buf = ctx.alloc().buffer();
            buf.writeInt(MAGIC_NUMBER_RESPONSE);
            // FIXME: implement
            out.add(buf);
        }
        else {
            throw new EncoderException("Unknown CyclonMessage type: " + StringUtil.simpleClassName(msg));
        }
    }

    @Override
    protected void decode(final ChannelHandlerContext ctx,
                          final ByteBuf in,
                          final List<Object> out) throws Exception {
        if (in.readableBytes() >= MIN_MESSAGE_LENGTH) {
            in.markReaderIndex();
            final int magicNumber = in.readInt();
            final UnsignedInteger sequenceNo = UnsignedInteger.of(in.readUnsignedInt());
            switch (magicNumber) {
                case MAGIC_NUMBER_REQUEST: {
                    out.add(new GoBackNArqData(sequenceNo, in.retain()));
                    break;
                }
                case MAGIC_NUMBER_RESPONSE: {
                    out.add(new GoBackNArqAck(sequenceNo));
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
}
