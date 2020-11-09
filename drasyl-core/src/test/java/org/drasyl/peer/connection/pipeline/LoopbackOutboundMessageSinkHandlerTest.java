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
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.drasyl.peer.connection.message.IdentityMessage;
import org.drasyl.peer.connection.message.Message;
import org.drasyl.peer.connection.message.WhoisMessage;
import org.drasyl.pipeline.EmbeddedPipeline;
import org.drasyl.pipeline.codec.TypeValidator;
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
class LoopbackOutboundMessageSinkHandlerTest {
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Identity identity;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private PeersManager peersManager;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private DrasylConfig config;

    @Test
    void shouldPassMessageIfNodeIsNotStarted(@Mock final CompressedPublicKey recipient,
                                             @Mock final ApplicationMessage message) {
        final EmbeddedPipeline pipeline = new EmbeddedPipeline(
                config,
                identity,
                TypeValidator.ofInboundValidator(config),
                TypeValidator.ofOutboundValidator(config),
                new LoopbackOutboundMessageSinkHandler(new AtomicBoolean(false), peersManager, Set.of())
        );
        final TestObserver<Message> outboundMessages = pipeline.outboundMessages(Message.class).test();

        pipeline.processOutbound(recipient, message);

        outboundMessages.awaitCount(1).assertValueCount(1);
    }

    @Test
    void shouldPassMessageIfRecipientIsNotLocalNode(@Mock final CompressedPublicKey recipient,
                                                    @Mock final ApplicationMessage message) {
        final EmbeddedPipeline pipeline = new EmbeddedPipeline(
                config,
                identity,
                TypeValidator.ofInboundValidator(config),
                TypeValidator.ofOutboundValidator(config),
                new LoopbackOutboundMessageSinkHandler(new AtomicBoolean(true), peersManager, Set.of())
        );
        final TestObserver<Message> outboundMessages = pipeline.outboundMessages(Message.class).test();

        pipeline.processOutbound(recipient, message);

        outboundMessages.awaitCount(1).assertValueCount(1);
    }

    @Test
    void shouldAddPeerAndProcessMessageOnApplicationMessage(@Mock final CompressedPublicKey recipient,
                                                            @Mock final ApplicationMessage message) {
        when(identity.getPublicKey()).thenReturn(recipient);

        final EmbeddedPipeline pipeline = new EmbeddedPipeline(
                config,
                identity,
                TypeValidator.ofInboundValidator(config),
                TypeValidator.ofOutboundValidator(config),
                new LoopbackOutboundMessageSinkHandler(new AtomicBoolean(true), peersManager, Set.of())
        );
        final TestObserver<Message> outboundMessages = pipeline.outboundMessages(Message.class).test();
        final CompletableFuture<Void> future = pipeline.processOutbound(recipient, message);

        future.join();

        outboundMessages.assertNoValues();
        verify(peersManager).addPeer(message.getSender());
    }

    @Test
    void shouldSetPeerInformationAndSendIdentityMessageOnWhoisMessage(@Mock final CompressedPublicKey recipient,
                                                                      @Mock(answer = RETURNS_DEEP_STUBS) final WhoisMessage message) {
        when(identity.getPublicKey()).thenReturn(recipient);

        final EmbeddedPipeline pipeline = new EmbeddedPipeline(
                config,
                identity,
                TypeValidator.ofInboundValidator(config),
                TypeValidator.ofOutboundValidator(config),
                new LoopbackOutboundMessageSinkHandler(new AtomicBoolean(true), peersManager, Set.of())
        );
        final TestObserver<Message> outboundMessages = pipeline.outboundMessages(Message.class).test();
        final CompletableFuture<Void> future = pipeline.processOutbound(recipient, message);

        future.join();

        outboundMessages.awaitCount(1).assertValueCount(1);
        verify(peersManager).setPeerInformation(message.getSender(), message.getPeerInformation());
    }

    @Test
    void shouldSetPeerInformationOnIdentityMessage(@Mock final CompressedPublicKey recipient,
                                                   @Mock(answer = RETURNS_DEEP_STUBS) final IdentityMessage message) {
        when(identity.getPublicKey()).thenReturn(recipient);

        final EmbeddedPipeline pipeline = new EmbeddedPipeline(
                config,
                identity,
                TypeValidator.ofInboundValidator(config),
                TypeValidator.ofOutboundValidator(config),
                new LoopbackOutboundMessageSinkHandler(new AtomicBoolean(true), peersManager, Set.of())
        );
        final TestObserver<Message> outboundMessages = pipeline.outboundMessages(Message.class).test();
        final CompletableFuture<Void> future = pipeline.processOutbound(recipient, message);

        future.join();

        outboundMessages.assertNoValues();
        verify(peersManager).setPeerInformation(message.getSender(), message.getPeerInformation());
    }
}