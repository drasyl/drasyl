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
package org.drasyl.remote.protocol;

import com.google.protobuf.MessageLite;
import com.goterl.lazysodium.utils.SessionPair;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import org.drasyl.crypto.Crypto;
import org.drasyl.remote.protocol.Protocol.PrivateHeader;
import org.drasyl.remote.protocol.Protocol.PublicHeader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Skeleton implementation for a {@link FullReadMessage}.
 */
abstract class AbstractFullReadMessage<T extends FullReadMessage<?>> implements FullReadMessage<T> {
    @Override
    public ArmedMessage arm(final Crypto cryptoInstance,
                            final SessionPair sessionPair) throws InvalidMessageFormatException {
        try (final ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            buildPrivateHeader().writeDelimitedTo(outputStream);
            buildBody().writeDelimitedTo(outputStream);
            final byte[] bytes = outputStream.toByteArray();

            final UnarmedMessage unarmedMessage = UnarmedMessage.of(getNonce(), getNetworkId(), getSender(), getProofOfWork(), getRecipient(), getHopCount(), getAgreementId(), bytes);
            return unarmedMessage.arm(cryptoInstance, sessionPair);
        }
        catch (final IOException e) {
            throw new InvalidMessageFormatException("Unable to arm message", e);
        }
    }

    @Override
    public void writeTo(final ByteBuf out) throws InvalidMessageFormatException {
        // message (partially) present as java objects. get bytes and transfer to buffer
        try (final ByteBufOutputStream outputStream = new ByteBufOutputStream(out)) {
            MAGIC_NUMBER.writeTo(outputStream);
            buildPublicHeader().writeDelimitedTo(outputStream);
            buildPrivateHeader().writeDelimitedTo(outputStream);
            buildBody().writeDelimitedTo(outputStream);
        }
        catch (final Exception e) {
            throw new InvalidMessageFormatException(e);
        }
    }

    protected PublicHeader buildPublicHeader() {
        final PublicHeader.Builder builder = PublicHeader.newBuilder()
                .setNonce(getNonce().toByteString())
                .setNetworkId(getNetworkId())
                .setSender(getSender().getBytes())
                .setProofOfWork(getProofOfWork().intValue())
                .setHopCount(getHopCount().getByte());

        if (getRecipient() != null) {
            builder.setRecipient(getRecipient().getBytes());
        }

        if (getAgreementId() != null) {
            builder.setAgreementId(getAgreementId().toByteString());
        }

        return builder.build();
    }

    protected abstract PrivateHeader buildPrivateHeader();

    protected abstract MessageLite buildBody();
}
