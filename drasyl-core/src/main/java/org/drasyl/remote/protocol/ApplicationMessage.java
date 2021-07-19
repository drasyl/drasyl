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

import com.google.auto.value.AutoValue;
import com.google.protobuf.ByteString;
import org.drasyl.annotation.Nullable;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.remote.handler.crypto.AgreementId;
import org.drasyl.remote.protocol.Protocol.Application;
import org.drasyl.remote.protocol.Protocol.PrivateHeader;

import static org.drasyl.remote.protocol.Nonce.randomNonce;
import static org.drasyl.remote.protocol.Protocol.MessageType.APPLICATION;

/*
 * Describes a message sent by an application running on drasyl.
 * <p>
 * This is an immutable object.
 */
@AutoValue
@SuppressWarnings("java:S118")
public abstract class ApplicationMessage extends AbstractFullReadMessage<ApplicationMessage> {
    /**
     * Returns the fully qualified class name of the payload. Returns {@code null} if the
     * application sent an empty message.
     *
     * @return the fully qualified class name of the payload
     */
    @Nullable
    public abstract String getType();

    /**
     * Returns the payload. Returns {@code null} if the application sent an empty message.
     *
     * @return the payload
     */
    @Nullable
    public abstract ByteString getPayload();

    public byte[] getPayloadAsByteArray() {
        if (getPayload() != null) {
            return getPayload().toByteArray();
        }
        else {
            return new byte[0];
        }
    }

    @Override
    public ApplicationMessage incrementHopCount() {
        return ApplicationMessage.of(getNonce(), getNetworkId(), getSender(), getProofOfWork(), getRecipient(), getHopCount().increment(), getAgreementId(), getType(), getPayload());
    }

    @Override
    public ApplicationMessage setAgreementId(final AgreementId agreementId) {
        return ApplicationMessage.of(getNonce(), getNetworkId(), getSender(), getProofOfWork(), getRecipient(), getHopCount(), agreementId, getType(), getPayload());
    }

    @Override
    protected PrivateHeader buildPrivateHeader() {
        return PrivateHeader.newBuilder()
                .setType(APPLICATION)
                .build();
    }

    @Override
    protected Application buildBody() {
        final Application.Builder builder = Application.newBuilder();

        if (getType() != null) {
            builder.setType(getType());
        }

        if (getPayload() != null) {
            builder.setPayload(getPayload());
        }

        return builder.build();
    }

    /**
     * Creates new application message.
     *
     * @param nonce       the nonce
     * @param networkId   the network id
     * @param sender      the public key of the sender
     * @param proofOfWork the proof of work of {@code sender}
     * @param recipient   the public key of the recipient
     * @param hopCount    the hop count
     * @param agreementId the agreement id
     * @param type        the fully qualified class name of the payload
     * @param payload     the serialized payload
     * @throws NullPointerException if {@code nonce},  {@code sender}, {@code proofOfWork}, {@code
     *                              recipient}, or {@code hopCount} is {@code null}
     */
    @SuppressWarnings("java:S107")
    public static ApplicationMessage of(final Nonce nonce,
                                        final int networkId,
                                        final IdentityPublicKey sender,
                                        final ProofOfWork proofOfWork,
                                        final IdentityPublicKey recipient,
                                        final HopCount hopCount,
                                        final AgreementId agreementId,
                                        final String type,
                                        final ByteString payload) {
        return new AutoValue_ApplicationMessage(
                nonce,
                networkId,
                sender,
                proofOfWork,
                hopCount,
                agreementId,
                recipient,
                type,
                payload
        );
    }

    /**
     * Creates new application message with random {@link Nonce}, minimal {@link HopCount} value,
     * and no {@link AgreementId}.
     *
     * @param networkId   the network id
     * @param sender      the public key of the sender
     * @param proofOfWork the proof of work of {@code sender}
     * @param recipient   the public key of the recipient
     * @param type        the fully qualified class name of the payload
     * @param payload     the serialized payload
     * @throws NullPointerException if {@code sender}, {@code proofOfWork}, {@code recipient}, or
     *                              {@code correspondingId} is {@code null}
     */
    @SuppressWarnings("java:S107")
    public static ApplicationMessage of(final int networkId,
                                        final IdentityPublicKey sender,
                                        final ProofOfWork proofOfWork,
                                        final IdentityPublicKey recipient,
                                        final String type,
                                        final ByteString payload) {
        return of(
                randomNonce(),
                networkId,
                sender,
                proofOfWork,
                recipient,
                HopCount.of(),
                null,
                type,
                payload
        );
    }

    /**
     * Creates new application message.
     *
     * @param nonce       the nonce
     * @param networkId   the network id
     * @param sender      the public key of the sender
     * @param proofOfWork the proof of work of {@code sender}
     * @param recipient   the public key of the recipient
     * @param hopCount    the hop count
     * @param agreementId the agreement id
     * @throws NullPointerException if {@code nonce},  {@code sender}, {@code proofOfWork}, {@code
     *                              recipient}, or {@code hopCount} is {@code null}
     */
    @SuppressWarnings("java:S107")
    public static ApplicationMessage of(final Nonce nonce,
                                        final int networkId,
                                        final IdentityPublicKey sender,
                                        final ProofOfWork proofOfWork,
                                        final IdentityPublicKey recipient,
                                        final HopCount hopCount,
                                        final AgreementId agreementId,
                                        final Application body) {
        return of(
                nonce,
                networkId,
                sender,
                proofOfWork,
                recipient,
                hopCount,
                agreementId,
                body.getType(),
                body.getPayload()
        );
    }
}
