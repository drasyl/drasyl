/*
 * Copyright (c) 2020.
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
package org.drasyl.pipeline;

import io.netty.channel.Channel;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelPromise;
import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.DrasylConfig;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.PeerChannelGroup;
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.codec.TypeValidator;
import org.drasyl.util.Pair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;

import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuperPeerInboundMessageSinkHandlerTest {
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Identity identity;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private PeerChannelGroup channelGroup;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private PeersManager peersManager;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private DrasylConfig config;

    @Test
    void shouldSendMessageToSuperPeer(@Mock(answer = RETURNS_DEEP_STUBS) final ApplicationMessage message) {
        final EmbeddedPipeline pipeline = new EmbeddedPipeline(
                config,
                identity,
                TypeValidator.ofInboundValidator(config),
                TypeValidator.ofOutboundValidator(config),
                new SuperPeerInboundMessageSinkHandler(channelGroup, peersManager)
        );
        final TestObserver<Object> outboundMessages = pipeline.outboundOnlyMessages().test();
        final Channel channel = mock(Channel.class);
        final ChannelPromise promise = new DefaultChannelPromise(channel);
        when(channelGroup.writeAndFlush(any(CompressedPublicKey.class), any(Object.class))).thenReturn(promise);

        final CompletableFuture<Void> future = pipeline.processInbound(message);
        promise.setSuccess();

        future.join();

        outboundMessages.assertNoValues();
        verify(channelGroup).writeAndFlush(peersManager.getSuperPeerKey(), message);
    }

    @Test
    void shouldPassMessageIfWriteAndFlushFails(@Mock(answer = RETURNS_DEEP_STUBS) final ApplicationMessage message) {
        when(channelGroup.writeAndFlush(any(CompressedPublicKey.class), any(Object.class))).thenThrow(IllegalArgumentException.class);

        final EmbeddedPipeline pipeline = new EmbeddedPipeline(
                config,
                identity,
                TypeValidator.ofInboundValidator(config),
                TypeValidator.ofOutboundValidator(config),
                new SuperPeerInboundMessageSinkHandler(channelGroup, peersManager)
        );
        final TestObserver<Pair<Address, Object>> inboundMessages = pipeline.inboundMessages().test();

        pipeline.processInbound(message);

        inboundMessages.awaitCount(1).assertValueCount(1);
    }

    @Test
    void shouldPassMessageIfNoSuperPeerExist(@Mock(answer = RETURNS_DEEP_STUBS) final ApplicationMessage message) {
        when(peersManager.getSuperPeerKey()).thenReturn(null);

        final EmbeddedPipeline pipeline = new EmbeddedPipeline(
                config,
                identity,
                TypeValidator.ofInboundValidator(config),
                TypeValidator.ofOutboundValidator(config),
                new SuperPeerInboundMessageSinkHandler(channelGroup, peersManager)
        );
        final TestObserver<Pair<Address, Object>> inboundMessages = pipeline.inboundMessages().test();
        final Channel channel = mock(Channel.class);
        final ChannelPromise promise = new DefaultChannelPromise(channel);
        when(channelGroup.writeAndFlush(any(CompressedPublicKey.class), any(Object.class))).thenReturn(promise);

        final CompletableFuture<Void> future = pipeline.processInbound(message);
        promise.setSuccess();

        future.join();

        inboundMessages.awaitCount(1).assertValueCount(1);
    }
}