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

import com.google.protobuf.MessageLite;
import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.DrasylConfig;
import org.drasyl.crypto.CryptoException;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.EmbeddedPipeline;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.address.InetSocketAddressWrapper;
import org.drasyl.remote.protocol.AddressedByteBuf;
import org.drasyl.remote.protocol.AddressedIntermediateEnvelope;
import org.drasyl.remote.protocol.IntermediateEnvelope;
import org.drasyl.remote.protocol.Protocol.Application;
import org.drasyl.util.ReferenceCountUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class Message2ByteBufHandlerTest {
    @Mock
    private DrasylConfig config;
    @Mock
    private Identity identity;
    @Mock
    private PeersManager peersManager;

    @Test
    void shouldConvertEnvelopeToByteBuf(@Mock final InetSocketAddressWrapper sender,
                                        @Mock final InetSocketAddressWrapper recipient) throws CryptoException, IOException {
        final IntermediateEnvelope<Application> messageEnvelope = IntermediateEnvelope.application(1337, CompressedPublicKey.of("034a450eb7955afb2f6538433ae37bd0cbc09745cf9df4c7ccff80f8294e6b730d"), ProofOfWork.of(3556154), CompressedPublicKey.of("0229041b273dd5ee1c2bef2d77ae17dbd00d2f0a2e939e22d42ef1c4bf05147ea9"), byte[].class.getName(), "Hello World".getBytes());
        final AddressedIntermediateEnvelope<Application> addressedEnvelope = new AddressedIntermediateEnvelope<>(sender, recipient, messageEnvelope);

        final Message2ByteBufHandler handler = Message2ByteBufHandler.INSTANCE;
        final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler);
        final TestObserver<AddressedByteBuf> outboundMessages = pipeline.outboundMessages(AddressedByteBuf.class).test();
        pipeline.processOutbound(recipient, addressedEnvelope);

        outboundMessages.awaitCount(1)
                .assertValueCount(1)
                .assertValue(new AddressedByteBuf(sender, recipient, messageEnvelope.getOrBuildByteBuf()));

        ReferenceCountUtil.safeRelease(addressedEnvelope);
        pipeline.close();
    }

    @Test
    void shouldCompleteFutureExceptionallyWhenConversionFail(@Mock final Address recipient,
                                                             @Mock(answer = RETURNS_DEEP_STUBS) final AddressedIntermediateEnvelope<MessageLite> messageEnvelope) throws IOException {
        when(messageEnvelope.getContent().getOrBuildByteBuf()).thenThrow(RuntimeException.class);

        final Message2ByteBufHandler handler = Message2ByteBufHandler.INSTANCE;
        final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler);

        assertThrows(ExecutionException.class, () -> pipeline.processOutbound(recipient, messageEnvelope).get());
        pipeline.close();
    }
}
