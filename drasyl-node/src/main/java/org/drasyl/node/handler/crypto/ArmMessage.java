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
package org.drasyl.node.handler.crypto;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.drasyl.handler.remote.protocol.InvalidMessageFormatException;

public abstract class ArmMessage {
    public static final int LENGTH = 1;

    public abstract MessageType getType();

    public abstract void writeBody(ByteBuf byteBuf);

    public ByteBuf toByteBuf() {
        final ByteBuf byteBuf = Unpooled.buffer();
        byteBuf.writeByte(getType().value);
        writeBody(byteBuf);

        return byteBuf;
    }

    public static ByteBuf fromApplication(final ByteBuf msg) {
        final ByteBuf byteBuf = Unpooled.buffer();
        byteBuf.writeByte(MessageType.APPLICATION.value);

        return Unpooled.wrappedBuffer(byteBuf, msg);
    }

    public static Object of(final ByteBuf byteBuf) throws InvalidMessageFormatException {
        if (byteBuf.readableBytes() < LENGTH) {
            throw new InvalidMessageFormatException("ArmMessage requires " + LENGTH + " readable bytes. Only " + byteBuf.readableBytes() + " left.");
        }
        final MessageType type = MessageType.forNumber(byteBuf.readByte());

        switch (type) {
            case ACKNOWLEDGEMENT:
                return AcknowledgementMessage.of(byteBuf);
            case KEY_EXCHANGE:
                return KeyExchangeMessage.of(byteBuf);
            default:
                return byteBuf.slice();
        }
    }

    enum MessageType {
        ACKNOWLEDGEMENT((byte) 0),
        KEY_EXCHANGE((byte) 1),
        APPLICATION((byte) 2);
        private final byte value;

        MessageType(final byte value) {
            this.value = value;
        }

        public final byte getByte() {
            return value;
        }

        public static MessageType forNumber(final byte value) {
            switch (value) {
                case 0:
                    return ACKNOWLEDGEMENT;
                case 1:
                    return KEY_EXCHANGE;
                default:
                    return APPLICATION;
            }
        }
    }
}
