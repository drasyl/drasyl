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
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import org.drasyl.annotation.Nullable;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.ProofOfWork;

/**
 * This class models the public header of a drasyl protocol message. The header is structured as
 * follows:
 * <ul>
 * <li><b>MagicNumber</b>: The 4 bytes magic number value. Is a static value for all messages. Used to filter unrelated messages.</li>
 * <li><b>Armed</b>: The 1 byte armed value. Indicates, if the message is armed or not.</li>
 * <li><b>HopCount</b>: The 1 byte hop count value. Is incremented on each hop. Used to avoid loops.</li>
 * <li><b>NetworkId</b>: The 4 bytes network id value. Is a unique network-wide value. Used to filter messages from other networks.</li>
 * <li><b>Nonce</b>: The 24 bytes nonce value. Is used for encryption and as message id.</li>
 * <li><b>Recipient</b>: The 32 bytes recipient address. This value is optional. If not set it MUST be sent as 0.</li>
 * <li><b>Sender</b>: The 32 bytes sender address.</li>
 * <li><b>ProofOfWork</b>: The 4 bytes proof of work for the sender address.</li>
 * </ul>
 * The public header is only authenticated and protected at the 5th byte. The magic number, and hop count are not protected. This allows us to update the hop count in-place.
 */
@AutoValue
public abstract class PublicHeader {
    public static final int LENGTH = 98;

    public abstract HopCount getHopCount();

    public abstract boolean getArmed();

    public abstract int getNetworkId();

    public abstract Nonce getNonce();

    @Nullable
    public abstract IdentityPublicKey getRecipient();

    public abstract IdentityPublicKey getSender();

    public abstract ProofOfWork getProofOfWork();

    public static PublicHeader of(final HopCount hopCount,
                                  final boolean isArmed,
                                  final int networkId,
                                  final Nonce nonce,
                                  final IdentityPublicKey recipient,
                                  final IdentityPublicKey sender,
                                  final ProofOfWork proofOfWork) {
        return new AutoValue_PublicHeader(hopCount, isArmed, networkId, nonce, recipient, sender, proofOfWork);
    }

    public static PublicHeader of(final RemoteMessage msg) {
        return of(msg.getHopCount(), msg.getArmed(), msg.getNetworkId(), msg.getNonce(), msg.getRecipient(), msg.getSender(), msg.getProofOfWork());
    }

    /**
     * Builds the authentication tag from this public header.
     *
     * @return the authentication tag
     */
    public byte[] buildAuthTag() {
        final ByteBuf byteBuf = Unpooled.buffer(LENGTH - 1);

        try {
            writeTo(byteBuf, false);
            return ByteBufUtil.getBytes(byteBuf);
        }
        finally {
            byteBuf.release();
        }
    }

    /**
     * Writes this header to the buffer {@code byteBuf}.
     *
     * @param byteBuf      writes this header to the given buffer
     * @param withHopCount if the hop count should be included
     */
    public void writeTo(final ByteBuf byteBuf, final boolean withHopCount) {
        final byte[] recipientBuffer = getRecipient() == null ? IdentityPublicKey.ZERO_ID.toByteArray() : getRecipient().toByteArray();

        if (withHopCount) {
            byteBuf.writeByte(getHopCount().getByte());          //  1 byte
        }
        byteBuf.writeBoolean(getArmed())                        //  1 byte
                .writeInt(getNetworkId())                       //  4 bytes
                .writeBytes(getNonce().toByteArray())           // 24 bytes
                .writeBytes(recipientBuffer)                    // 32 bytes
                .writeBytes(getSender().toByteArray())          // 32 bytes
                .writeInt(getProofOfWork().intValue());         //  4 bytes
    }

    /**
     * Writes this header to the buffer {@code byteBuf}. Similar to {@link #writeTo(ByteBuf,
     * boolean) #writeTo(ByteBuf, true)}.
     *
     * @param byteBuf writes this header to the given buffer
     */
    public void writeTo(final ByteBuf byteBuf) {
        writeTo(byteBuf, true);
    }

    public static PublicHeader of(final ByteBuf byteBuf) throws InvalidMessageFormatException {
        if (byteBuf.readableBytes() < LENGTH) {
            throw new InvalidMessageFormatException("PublicHeader requires " + LENGTH + " readable bytes. Only " + byteBuf.readableBytes() + " left.");
        }

        final HopCount hopCount;
        final boolean isArmed;
        final int networkId;
        final Nonce nonce;
        IdentityPublicKey recipient;
        final IdentityPublicKey sender;
        final ProofOfWork proofOfWork;

        hopCount = HopCount.of(byteBuf.readByte());
        isArmed = byteBuf.readBoolean();
        networkId = byteBuf.readInt();

        final byte[] nonceBuffer = new byte[Nonce.NONCE_LENGTH];
        byteBuf.readBytes(nonceBuffer);
        nonce = Nonce.of(nonceBuffer);

        final byte[] recipientBuffer = new byte[IdentityPublicKey.KEY_LENGTH_AS_BYTES];
        byteBuf.readBytes(recipientBuffer);
        recipient = IdentityPublicKey.of(recipientBuffer);

        if (recipient == IdentityPublicKey.ZERO_ID) {
            recipient = null;
        }

        final byte[] senderBuffer = new byte[IdentityPublicKey.KEY_LENGTH_AS_BYTES];
        byteBuf.readBytes(senderBuffer);
        sender = IdentityPublicKey.of(senderBuffer);

        proofOfWork = ProofOfWork.of(byteBuf.readInt());

        return of(hopCount, isArmed, networkId, nonce, recipient, sender, proofOfWork);
    }
}
