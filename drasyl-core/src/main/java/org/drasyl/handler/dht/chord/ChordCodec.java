package org.drasyl.handler.dht.chord;

import io.netty.buffer.ByteBuf;
import io.netty.channel.AddressedEnvelope;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.EncoderException;
import io.netty.handler.codec.MessageToMessageCodec;
import io.netty.util.internal.StringUtil;
import org.drasyl.channel.OverlayAddressedMessage;
import org.drasyl.handler.dht.chord.message.ChordMessage;
import org.drasyl.handler.dht.chord.message.Closest;
import org.drasyl.handler.dht.chord.message.FindSuccessor;
import org.drasyl.handler.dht.chord.message.FoundSuccessor;
import org.drasyl.handler.dht.chord.message.MyClosest;
import org.drasyl.identity.IdentityPublicKey;

import java.net.SocketAddress;
import java.util.List;

/**
 * Encodes {@link ChordMessage} messages to {@link ByteBuf}s and vice versa.
 */
@Sharable
public class ChordCodec extends MessageToMessageCodec<OverlayAddressedMessage<ByteBuf>, OverlayAddressedMessage<ChordMessage>> {
    public static final int MAGIC_NUMBER_CLOSEST = -256235100;
    public static final int MAGIC_NUMBER_MY_CLOSEST = -256235099;
    public static final int MAGIC_NUMBER_YOUR_SUCCESSOR = -256235098;
    public static final int MAGIC_NUMBER_MY_SUCCESSOR = -256235097;
    public static final int MAGIC_NUMBER_NOTHING_SUCCESSOR = -256235096;
    public static final int MAGIC_NUMBER_YOUR_PREDECESSOR = -256235095;
    public static final int MAGIC_NUMBER_MY_PREDECESSOR = -256235094;
    public static final int MAGIC_NUMBER_NOTHING_PREDECESSOR = -256235093;
    public static final int MAGIC_NUMBER_FIND_SUCCESSOR = -256235092;
    public static final int MAGIC_NUMBER_FOUND_SUCCESSOR = -256235091;
    public static final int MAGIC_NUMBER_I_AM_PRE = -256235090;
    public static final int MAGIC_NUMBER_NOTIFIED = -256235089;
    public static final int MAGIC_NUMBER_KEEP = -256235088;
    public static final int MAGIC_NUMBER_ALIVE = -256235087;
    // magic number: 4 bytes
    public static final int MIN_MESSAGE_LENGTH = 4;

    @SuppressWarnings("unchecked")
    @Override
    public boolean acceptOutboundMessage(Object msg) {
        return msg instanceof AddressedEnvelope && ((AddressedEnvelope<?, SocketAddress>) msg).content() instanceof ChordMessage;
    }

    @Override
    protected void encode(final ChannelHandlerContext ctx,
                          final OverlayAddressedMessage<ChordMessage> msg,
                          final List<Object> out) throws Exception {
        if (msg.content() instanceof Closest) {
            final ByteBuf buf = ctx.alloc().buffer();
            buf.writeInt(MAGIC_NUMBER_CLOSEST);
            buf.writeLong(((Closest) msg.content()).getId());
            out.add(msg.replace(buf));
        }
        else if (msg.content() instanceof MyClosest) {
            final ByteBuf buf = ctx.alloc().buffer();
            buf.writeInt(MAGIC_NUMBER_MY_CLOSEST);
            buf.writeBytes(((MyClosest) msg.content()).getAddress().toByteArray());
            out.add(msg.replace(buf));
        }
        else if (msg.content() instanceof FindSuccessor) {
            final ByteBuf buf = ctx.alloc().buffer();
            buf.writeInt(MAGIC_NUMBER_FIND_SUCCESSOR);
            buf.writeLong(((FindSuccessor) msg.content()).getId());
            out.add(msg.replace(buf));
        }
        else if (msg.content() instanceof FoundSuccessor) {
            final ByteBuf buf = ctx.alloc().buffer();
            buf.writeInt(MAGIC_NUMBER_FOUND_SUCCESSOR);
            buf.writeBytes(((FoundSuccessor) msg.content()).getAddress().toByteArray());
            out.add(msg.replace(buf));
        }
        else {
            throw new EncoderException("Unknown ChordMessage type: " + StringUtil.simpleClassName(msg));
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean acceptInboundMessage(Object msg) throws Exception {
        return msg instanceof AddressedEnvelope && ((AddressedEnvelope<?, SocketAddress>) msg).content() instanceof ByteBuf;
    }

    @Override
    protected void decode(final ChannelHandlerContext ctx,
                          final OverlayAddressedMessage<ByteBuf> msg,
                          final List<Object> out) throws Exception {
        if (msg.content().readableBytes() >= MIN_MESSAGE_LENGTH) {
            msg.content().markReaderIndex();
            final int magicNumber = msg.content().readInt();
            switch (magicNumber) {
                case MAGIC_NUMBER_CLOSEST: {
                    out.add(msg.replace(Closest.of(msg.content().readLong())));
                    break;
                }
                case MAGIC_NUMBER_MY_CLOSEST: {
                    final byte[] addressBuffer = new byte[IdentityPublicKey.KEY_LENGTH_AS_BYTES];
                    msg.content().readBytes(addressBuffer);
                    out.add(msg.replace(MyClosest.of(IdentityPublicKey.of(addressBuffer))));
                    break;
                }
                case MAGIC_NUMBER_FIND_SUCCESSOR: {
                    out.add(msg.replace(FindSuccessor.of(msg.content().readLong())));
                    break;
                }
                case MAGIC_NUMBER_FOUND_SUCCESSOR: {
                    final byte[] addressBuffer = new byte[IdentityPublicKey.KEY_LENGTH_AS_BYTES];
                    msg.content().readBytes(addressBuffer);
                    out.add(msg.replace(FoundSuccessor.of(IdentityPublicKey.of(addressBuffer))));
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
