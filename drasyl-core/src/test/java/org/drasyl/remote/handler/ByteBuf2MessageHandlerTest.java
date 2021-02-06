/*
 * Copyright (c) 2020-2021.
 *
 * This file is part of drasyl.
 *
 *  drasyl is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  drasyl is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.drasyl.remote.handler;

import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.DrasylConfig;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.EmbeddedPipeline;
import org.drasyl.pipeline.address.InetSocketAddressWrapper;
import org.drasyl.remote.protocol.AddressedByteBuf;
import org.drasyl.remote.protocol.AddressedIntermediateEnvelope;
import org.drasyl.remote.protocol.IntermediateEnvelope;
import org.drasyl.remote.protocol.MessageId;
import org.drasyl.remote.protocol.Protocol.Acknowledgement;
import org.drasyl.util.ReferenceCountUtil;
import org.drasyl.util.TypeReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

@ExtendWith(MockitoExtension.class)
class ByteBuf2MessageHandlerTest {
    private final MessageId correspondingId = MessageId.of("412176952b5b81fd13f84a7c");
    @Mock
    private DrasylConfig config;
    @Mock
    private Identity identity;
    @Mock
    private PeersManager peersManager;
    private CompressedPublicKey senderPublicKey;
    private ProofOfWork proofOfWork;
    private CompressedPublicKey recipientPublicKey;

    @BeforeEach
    void setUp() {
        senderPublicKey = CompressedPublicKey.of("0229041b273dd5ee1c2bef2d77ae17dbd00d2f0a2e939e22d42ef1c4bf05147ea9");
        recipientPublicKey = CompressedPublicKey.of("030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3");
        proofOfWork = ProofOfWork.of(1);
    }

    @Test
    void shouldConvertByteBufToEnvelope(@Mock final InetSocketAddressWrapper senderAddress,
                                        @Mock final InetSocketAddressWrapper recipientAddress) throws IOException {
        final IntermediateEnvelope<Acknowledgement> acknowledgementMessage = IntermediateEnvelope.acknowledgement(1337, senderPublicKey, proofOfWork, recipientPublicKey, correspondingId);
        final AddressedByteBuf byteBuf = new AddressedByteBuf(senderAddress, recipientAddress, acknowledgementMessage.getOrBuildByteBuf());

        final ByteBuf2MessageHandler handler = ByteBuf2MessageHandler.INSTANCE;
        final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler);
        final TestObserver<AddressedIntermediateEnvelope<?>> inboundMessages = pipeline.inboundMessages(new TypeReference<AddressedIntermediateEnvelope<?>>() {
        }).test();
        pipeline.processInbound(senderPublicKey, byteBuf);

        inboundMessages.awaitCount(1)
                .assertValueCount(1);

        ReferenceCountUtil.safeRelease(byteBuf);
        pipeline.close();
    }
}
