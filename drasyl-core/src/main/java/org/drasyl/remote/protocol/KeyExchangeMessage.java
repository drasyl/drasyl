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
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.KeyAgreementPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.remote.handler.crypto.AgreementId;
import org.drasyl.remote.protocol.Protocol.KeyExchange;
import org.drasyl.remote.protocol.Protocol.PrivateHeader;

import static org.drasyl.remote.protocol.Nonce.randomNonce;
import static org.drasyl.remote.protocol.Protocol.MessageType.KEY_EXCHANGE;

/**
 * Used to perform a PFS encryption key exchange.
 * <p>
 * This is an immutable object.
 */
@AutoValue
@SuppressWarnings("java:S118")
public abstract class KeyExchangeMessage extends AbstractFullReadMessage<KeyExchangeMessage> {
    public abstract KeyAgreementPublicKey getSessionKey();

    @Override
    public KeyExchangeMessage incrementHopCount() {
        return KeyExchangeMessage.of(getNonce(), getNetworkId(), getSender(), getProofOfWork(), getRecipient(), getHopCount().increment(), getAgreementId(), getSessionKey());
    }

    @Override
    public KeyExchangeMessage setAgreementId(final AgreementId agreementId) {
        return KeyExchangeMessage.of(getNonce(), getNetworkId(), getSender(), getProofOfWork(), getRecipient(), getHopCount(), agreementId, getSessionKey());
    }

    @Override
    protected PrivateHeader buildPrivateHeader() {
        return PrivateHeader.newBuilder()
                .setType(KEY_EXCHANGE)
                .build();
    }

    @Override
    protected KeyExchange buildBody() {
        return KeyExchange.newBuilder()
                .setSessionKey(getSessionKey().getBytes())
                .build();
    }

    /**
     * Creates new key exchange message.
     *
     * @param nonce       the nonce
     * @param networkId   the network id
     * @param sender      the public key of the sender
     * @param proofOfWork the proof of work of {@code sender}
     * @param recipient   the public key of the recipient
     * @param hopCount    the hop count
     * @param agreementId the agreement id
     * @param sessionKey  the session key
     * @throws NullPointerException if {@code nonce},  {@code sender}, {@code proofOfWork}, {@code
     *                              recipient}, {@code hopCount}, or {@code sessionKey} is {@code
     *                              null}
     */
    @SuppressWarnings("java:S107")
    public static KeyExchangeMessage of(final Nonce nonce,
                                        final int networkId,
                                        final IdentityPublicKey sender,
                                        final ProofOfWork proofOfWork,
                                        final IdentityPublicKey recipient,
                                        final HopCount hopCount,
                                        final AgreementId agreementId,
                                        final KeyAgreementPublicKey sessionKey) {
        return new AutoValue_KeyExchangeMessage(
                nonce,
                networkId,
                sender,
                proofOfWork,
                hopCount,
                agreementId,
                recipient,
                sessionKey
        );
    }

    /**
     * Creates new key exchange message with random {@link Nonce}, minimal {@link HopCount} value,
     * and no {@link AgreementId}.
     *
     * @param networkId   the network id
     * @param sender      the public key of the sender
     * @param proofOfWork the proof of work of {@code sender}
     * @param recipient   the public key of the recipient
     * @param sessionKey  the session key
     * @throws NullPointerException if {@code nonce},  {@code sender}, {@code proofOfWork}, {@code
     *                              recipient}, {@code hopCount}, or {@code sessionKey} is {@code
     *                              null}
     */
    public static KeyExchangeMessage of(final int networkId,
                                        final IdentityPublicKey sender,
                                        final ProofOfWork proofOfWork,
                                        final IdentityPublicKey recipient,
                                        final KeyAgreementPublicKey sessionKey) {
        return of(
                randomNonce(),
                networkId,
                sender,
                proofOfWork,
                recipient,
                HopCount.of(),
                null,
                sessionKey
        );
    }

    /**
     * Creates new key exchange message.
     *
     * @param nonce       the nonce
     * @param networkId   the network id
     * @param sender      the public key of the sender
     * @param proofOfWork the proof of work of {@code sender}
     * @param recipient   the public key of the recipient
     * @param hopCount    the hop count
     * @param agreementId the agreement id
     * @throws NullPointerException if {@code nonce},  {@code sender}, {@code proofOfWork}, {@code
     *                              recipient}, {@code hopCount}, or {@code body.getSessionKey()} is
     *                              {@code null}
     */
    @SuppressWarnings("java:S107")
    public static KeyExchangeMessage of(final Nonce nonce,
                                        final int networkId,
                                        final IdentityPublicKey sender,
                                        final ProofOfWork proofOfWork,
                                        final IdentityPublicKey recipient,
                                        final HopCount hopCount,
                                        final AgreementId agreementId,
                                        final KeyExchange body) {
        return of(
                nonce,
                networkId,
                sender,
                proofOfWork,
                recipient,
                hopCount,
                agreementId,
                KeyAgreementPublicKey.of(body.getSessionKey())
        );
    }
}
