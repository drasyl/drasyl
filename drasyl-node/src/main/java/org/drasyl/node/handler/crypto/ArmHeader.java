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
import io.netty.buffer.DefaultByteBufHolder;
import org.drasyl.crypto.sodium.LazyDrasylSodium;
import org.drasyl.handler.remote.protocol.Nonce;

import java.util.Objects;

public class ArmHeader extends DefaultByteBufHolder {
    public static final int MIN_LENGTH = 28 + LazyDrasylSodium.XCHACHA20POLY1305_IETF_ABYTES;
    private final AgreementId agreementId;
    private final Nonce nonce;

    public ArmHeader(final AgreementId agreementId,
                     final Nonce nonce,
                     final ByteBuf data) {
        super(data);
        this.agreementId = agreementId;
        this.nonce = nonce;
    }

    public static ArmHeader of(final AgreementId agreementId,
                               final Nonce nonce,
                               final ByteBuf data) {
        return new ArmHeader(agreementId, nonce, data);
    }

    public AgreementId getAgreementId() {
        return agreementId;
    }

    public Nonce getNonce() {
        return nonce;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final ArmHeader that = (ArmHeader) o;
        return Objects.equals(agreementId, that.agreementId) && Objects.equals(nonce, that.nonce);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), agreementId, nonce);
    }

    @Override
    public String toString() {
        return "ArmHeader{" +
                "agreementId=" + agreementId +
                ", nonce=" + nonce +
                ", content=" + content() +
                '}';
    }
}
