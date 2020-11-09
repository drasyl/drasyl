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
package org.drasyl.peer.connection.pipeline;

import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.DrasylConfig;
import org.drasyl.event.Event;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.drasyl.peer.connection.message.IdentityMessage;
import org.drasyl.peer.connection.message.WhoisMessage;
import org.drasyl.pipeline.EmbeddedPipeline;
import org.drasyl.pipeline.codec.TypeValidator;
import org.drasyl.util.Pair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoopbackInboundMessageSinkHandlerTest {
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Identity identity;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private PeersManager peersManager;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private DrasylConfig config;

    @Test
    void shouldPassAllEvents(@Mock final Event event) {
        final EmbeddedPipeline pipeline = new EmbeddedPipeline(
                config,
                identity,
                TypeValidator.ofInboundValidator(config),
                TypeValidator.ofOutboundValidator(config),
                new LoopbackInboundMessageSinkHandler(new AtomicBoolean(false), peersManager, Set.of())
        );
        final TestObserver<Event> inboundEvents = pipeline.inboundEvents().test();

        pipeline.processInbound(event);

        inboundEvents.awaitCount(1).assertValueCount(1);
        inboundEvents.assertValue(event);
    }

    @Test
    void shouldConsumeMessageIfNodeIsNotStarted(@Mock(answer = RETURNS_DEEP_STUBS) final ApplicationMessage message,
                                                @Mock final CompressedPublicKey recipient) {
        when(message.getRecipient()).thenReturn(recipient);
        when(identity.getPublicKey()).thenReturn(recipient);

        final EmbeddedPipeline pipeline = new EmbeddedPipeline(
                config,
                identity,
                TypeValidator.ofInboundValidator(config),
                TypeValidator.ofOutboundValidator(config),
                new LoopbackInboundMessageSinkHandler(new AtomicBoolean(false), peersManager, Set.of())
        );
        final TestObserver<Pair<CompressedPublicKey, Object>> inboundMessages = pipeline.inboundMessages().test();

        pipeline.processInbound(message);

        inboundMessages.assertNoValues();
    }

    @Test
    void shouldConsumeMessageIfRecipientIsNotLocalNode(@Mock final ApplicationMessage message) {
        final EmbeddedPipeline pipeline = new EmbeddedPipeline(
                config,
                identity,
                TypeValidator.ofInboundValidator(config),
                TypeValidator.ofOutboundValidator(config),
                new LoopbackInboundMessageSinkHandler(new AtomicBoolean(true), peersManager, Set.of())
        );
        final TestObserver<Pair<CompressedPublicKey, Object>> inboundMessages = pipeline.inboundMessages().test();

        pipeline.processInbound(message);

        inboundMessages.assertNoValues();
    }

    @Test
    void shouldAddPeerAndProcessMessageOnApplicationMessage(@Mock(answer = RETURNS_DEEP_STUBS) final ApplicationMessage message,
                                                            @Mock final CompressedPublicKey recipient) {
        when(message.getRecipient()).thenReturn(recipient);
        when(identity.getPublicKey()).thenReturn(recipient);

        final EmbeddedPipeline pipeline = new EmbeddedPipeline(
                config,
                identity,
                TypeValidator.ofInboundValidator(config),
                TypeValidator.ofOutboundValidator(config),
                new LoopbackInboundMessageSinkHandler(new AtomicBoolean(true), peersManager, Set.of())
        );
        final TestObserver<Pair<CompressedPublicKey, Object>> inboundMessages = pipeline.inboundMessages().test();
        final CompletableFuture<Void> future = pipeline.processInbound(message);

        future.join();

        inboundMessages.awaitCount(1).assertValueCount(1);
        verify(peersManager).addPeer(message.getSender());
    }

    @Test
    void shouldSetPeerInformationAndSendIdentityMessageOnWhoisMessage(@Mock(answer = RETURNS_DEEP_STUBS) final WhoisMessage message,
                                                                      @Mock final CompressedPublicKey recipient) {
        when(message.getRecipient()).thenReturn(recipient);
        when(identity.getPublicKey()).thenReturn(recipient);

        final EmbeddedPipeline pipeline = new EmbeddedPipeline(
                config,
                identity,
                TypeValidator.ofInboundValidator(config),
                TypeValidator.ofOutboundValidator(config),
                new LoopbackInboundMessageSinkHandler(new AtomicBoolean(true), peersManager, Set.of())
        );
        final TestObserver<Pair<CompressedPublicKey, Object>> inboundMessages = pipeline.inboundMessages().test();
        final CompletableFuture<Void> future = pipeline.processInbound(message);

        future.join();

        inboundMessages.assertNoValues();
        verify(peersManager).setPeerInformation(message.getSender(), message.getPeerInformation());
    }

    @Test
    void shouldSetPeerInformationOnIdentityMessage(@Mock(answer = RETURNS_DEEP_STUBS) final IdentityMessage message,
                                                   @Mock final CompressedPublicKey recipient) {
        when(message.getRecipient()).thenReturn(recipient);
        when(identity.getPublicKey()).thenReturn(recipient);

        final EmbeddedPipeline pipeline = new EmbeddedPipeline(
                config,
                identity,
                TypeValidator.ofInboundValidator(config),
                TypeValidator.ofOutboundValidator(config),
                new LoopbackInboundMessageSinkHandler(new AtomicBoolean(true), peersManager, Set.of())
        );
        final TestObserver<Pair<CompressedPublicKey, Object>> inboundMessages = pipeline.inboundMessages().test();
        final CompletableFuture<Void> future = pipeline.processInbound(message);

        future.join();

        inboundMessages.assertNoValues();
        verify(peersManager).setPeerInformation(message.getSender(), message.getPeerInformation());
    }
}