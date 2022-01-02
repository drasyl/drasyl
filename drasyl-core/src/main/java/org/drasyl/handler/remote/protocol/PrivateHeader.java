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
package org.drasyl.handler.remote.protocol;

import com.google.auto.value.AutoValue;
import io.netty.buffer.ByteBuf;
import org.drasyl.util.UnsignedShort;

import static org.drasyl.handler.remote.protocol.ArmedProtocolMessage.ARMED_HEADER_LENGTH;

/**
 * This class models the private header of a drasyl protocol message. If the {@link
 * PublicHeader#getArmed()} flag is set, this header is fully encrypted. The header is structured as
 * follows:
 * <ul>
 * <li><b>Type</b>: The 1 byte type value. Indicates the message type.</li>
 * <li><b>ArmedLength</b>: The 2 bytes armed length value. Indicates, the length of the encrypted portion of the message without AEAD tag.</li>
 * <li><b>AuthenticationHeader:</b> If ArmedLength value is greater than 0, a 16 bytes long authentication header is used. Otherwise this field is not present (0 bytes long).</li>
 * </ul>
 */
@SuppressWarnings({ "java:S109", "java:S118", "java:S1142" })
@AutoValue
public abstract class PrivateHeader {
    public static final int LENGTH = 3;
    public static final int ARMED_LENGTH = LENGTH + ARMED_HEADER_LENGTH;

    /**
     * @return the message type
     */
    public abstract MessageType getType();

    /**
     * @return the armed length
     */
    public abstract UnsignedShort getArmedLength();

    /**
     * Writes this header to the buffer {@code byteBuf}.
     *
     * @param byteBuf writes this header to the given buffer
     */
    public void writeTo(final ByteBuf byteBuf) {
        byteBuf.writeByte(getType().value);
        byteBuf.writeBytes(getArmedLength().toBytes());
    }

    public static UnsignedShort getArmedLength(final ByteBuf byteBuf) {
        byteBuf.markReaderIndex();
        byteBuf.readerIndex(byteBuf.readerIndex() + 1);
        final UnsignedShort armedLength = UnsignedShort.of(byteBuf.readUnsignedShort());
        byteBuf.resetReaderIndex();

        return armedLength;
    }

    public static PrivateHeader of(final ByteBuf byteBuf) throws InvalidMessageFormatException {
        if (byteBuf.readableBytes() < LENGTH) {
            throw new InvalidMessageFormatException("PrivateHeader requires " + LENGTH + " readable bytes. Only " + byteBuf.readableBytes() + " left.");
        }

        final MessageType type;
        final UnsignedShort armedLength;

        type = MessageType.forNumber(byteBuf.readByte());
        armedLength = UnsignedShort.of(byteBuf.readUnsignedShort());

        return of(type, armedLength);
    }

    public static PrivateHeader of(final MessageType type, final UnsignedShort armedLength) {
        return new AutoValue_PrivateHeader(type, armedLength);
    }

    public enum MessageType {
        ACKNOWLEDGEMENT((byte) 0),
        APPLICATION((byte) 1),
        DISCOVERY((byte) 2),
        UNITE((byte) 3);
        private final byte value;

        MessageType(final byte value) {
            this.value = value;
        }

        public final int getNumber() {
            return value;
        }

        public static MessageType forNumber(final byte value) {
            switch (value) {
                case 0:
                    return ACKNOWLEDGEMENT;
                case 1:
                    return APPLICATION;
                case 2:
                    return DISCOVERY;
                case 3:
                    return UNITE;
                default:
                    return null;
            }
        }
    }
}
