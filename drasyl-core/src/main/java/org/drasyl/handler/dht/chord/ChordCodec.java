package org.drasyl.handler.dht.chord;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.EncoderException;
import io.netty.handler.codec.MessageToMessageCodec;
import io.netty.util.internal.StringUtil;
import org.drasyl.channel.OverlayAddressedMessage;
import org.drasyl.handler.dht.chord.message.Alive;
import org.drasyl.handler.dht.chord.message.ChordMessage;
import org.drasyl.handler.dht.chord.message.Closest;
import org.drasyl.handler.dht.chord.message.FindSuccessor;
import org.drasyl.handler.dht.chord.message.FoundSuccessor;
import org.drasyl.handler.dht.chord.message.IAmPre;
import org.drasyl.handler.dht.chord.message.Keep;
import org.drasyl.handler.dht.chord.message.MyClosest;
import org.drasyl.handler.dht.chord.message.MyPredecessor;
import org.drasyl.handler.dht.chord.message.MySuccessor;
import org.drasyl.handler.dht.chord.message.NothingPredecessor;
import org.drasyl.handler.dht.chord.message.NothingSuccessor;
import org.drasyl.handler.dht.chord.message.Notified;
import org.drasyl.handler.dht.chord.message.YourPredecessor;
import org.drasyl.handler.dht.chord.message.YourSuccessor;
import org.drasyl.identity.IdentityPublicKey;

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
        else if (msg.content() instanceof YourSuccessor) {
            final ByteBuf buf = ctx.alloc().buffer();
            buf.writeInt(MAGIC_NUMBER_YOUR_SUCCESSOR);
            out.add(msg.replace(buf));
        }
        else if (msg.content() instanceof MySuccessor) {
            final ByteBuf buf = ctx.alloc().buffer();
            buf.writeInt(MAGIC_NUMBER_MY_SUCCESSOR);
            buf.writeBytes(((MySuccessor) msg.content()).getAddress().toByteArray());
            out.add(msg.replace(buf));
        }
        else if (msg.content() instanceof NothingSuccessor) {
            final ByteBuf buf = ctx.alloc().buffer();
            buf.writeInt(MAGIC_NUMBER_NOTHING_SUCCESSOR);
            out.add(msg.replace(buf));
        }
        else if (msg.content() instanceof YourPredecessor) {
            final ByteBuf buf = ctx.alloc().buffer();
            buf.writeInt(MAGIC_NUMBER_YOUR_PREDECESSOR);
            out.add(msg.replace(buf));
        }
        else if (msg.content() instanceof MyPredecessor) {
            final ByteBuf buf = ctx.alloc().buffer();
            buf.writeInt(MAGIC_NUMBER_MY_PREDECESSOR);
            buf.writeBytes(((MyPredecessor) msg.content()).getAddress().toByteArray());
            out.add(msg.replace(buf));
        }
        else if (msg.content() instanceof NothingPredecessor) {
            final ByteBuf buf = ctx.alloc().buffer();
            buf.writeInt(MAGIC_NUMBER_NOTHING_PREDECESSOR);
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
        else if (msg.content() instanceof IAmPre) {
            final ByteBuf buf = ctx.alloc().buffer();
            buf.writeInt(MAGIC_NUMBER_I_AM_PRE);
            out.add(msg.replace(buf));
        }
        else if (msg.content() instanceof Notified) {
            final ByteBuf buf = ctx.alloc().buffer();
            buf.writeInt(MAGIC_NUMBER_NOTIFIED);
            out.add(msg.replace(buf));
        }
        else if (msg.content() instanceof Keep) {
            final ByteBuf buf = ctx.alloc().buffer();
            buf.writeInt(MAGIC_NUMBER_KEEP);
            out.add(msg.replace(buf));
        }
        else if (msg.content() instanceof Alive) {
            final ByteBuf buf = ctx.alloc().buffer();
            buf.writeInt(MAGIC_NUMBER_ALIVE);
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
                case MAGIC_NUMBER_YOUR_SUCCESSOR: {
                    out.add(msg.replace(YourSuccessor.of()));
                    break;
                }
                case MAGIC_NUMBER_MY_SUCCESSOR: {
                    final byte[] addressBuffer = new byte[IdentityPublicKey.KEY_LENGTH_AS_BYTES];
                    msg.content().readBytes(addressBuffer);
                    out.add(msg.replace(MySuccessor.of(IdentityPublicKey.of(addressBuffer))));
                    break;
                }
                case MAGIC_NUMBER_NOTHING_SUCCESSOR: {
                    out.add(msg.replace(NothingSuccessor.of()));
                    break;
                }
                case MAGIC_NUMBER_YOUR_PREDECESSOR: {
                    out.add(msg.replace(YourPredecessor.of()));
                    break;
                }
                case MAGIC_NUMBER_MY_PREDECESSOR: {
                    final byte[] addressBuffer = new byte[IdentityPublicKey.KEY_LENGTH_AS_BYTES];
                    msg.content().readBytes(addressBuffer);
                    out.add(msg.replace(MyPredecessor.of(IdentityPublicKey.of(addressBuffer))));
                    break;
                }
                case MAGIC_NUMBER_NOTHING_PREDECESSOR: {
                    out.add(msg.replace(NothingPredecessor.of()));
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
                case MAGIC_NUMBER_I_AM_PRE: {
                    out.add(msg.replace(IAmPre.of()));
                    break;
                }
                case MAGIC_NUMBER_NOTIFIED: {
                    out.add(msg.replace(Notified.of()));
                    break;
                }
                case MAGIC_NUMBER_KEEP: {
                    out.add(msg.replace(Keep.of()));
                    break;
                }
                case MAGIC_NUMBER_ALIVE: {
                    out.add(msg.replace(Alive.of()));
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
