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

package org.drasyl.cli.command.wormhole;

import org.drasyl.DrasylConfig;
import org.drasyl.DrasylException;
import org.drasyl.DrasylNode;
import org.drasyl.DrasylNodeComponent;
import org.drasyl.crypto.Crypto;
import org.drasyl.event.Event;
import org.drasyl.event.MessageEvent;
import org.drasyl.event.NodeNormalTerminationEvent;
import org.drasyl.event.NodeUnrecoverableErrorEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.messenger.Messenger;
import org.drasyl.peer.Endpoint;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.PeerChannelGroup;
import org.drasyl.pipeline.Pipeline;
import org.drasyl.plugins.PluginManager;

import java.io.PrintStream;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@SuppressWarnings({ "java:S107" })
public class SendingWormholeNode extends DrasylNode {
    private final CompletableFuture<Void> doneFuture;
    private final PrintStream printStream;
    private final String password;
    private final AtomicBoolean sent;
    private String text;

    SendingWormholeNode(final CompletableFuture<Void> doneFuture,
                        final PrintStream printStream,
                        final String password,
                        final AtomicBoolean sent,
                        final DrasylConfig config,
                        final Identity identity,
                        final PeersManager peersManager,
                        final PeerChannelGroup channelGroup,
                        final Messenger messenger,
                        final Set<Endpoint> endpoints,
                        final AtomicBoolean acceptNewConnections,
                        final Pipeline pipeline,
                        final List<DrasylNodeComponent> components,
                        final PluginManager pluginManager,
                        final AtomicBoolean started,
                        final CompletableFuture<Void> startSequence,
                        final CompletableFuture<Void> shutdownSequence) {
        super(config, identity, peersManager, channelGroup, messenger, endpoints, acceptNewConnections, pipeline, components, pluginManager, started, startSequence, shutdownSequence);
        this.doneFuture = doneFuture;
        this.printStream = printStream;
        this.password = password;
        this.sent = sent;
    }

    public SendingWormholeNode(final DrasylConfig config,
                               final PrintStream printStream) throws DrasylException {
        super(DrasylConfig.newBuilder(config)
                .serverBindPort(0)
                .marshallingInboundAllowedTypes(List.of(WormholeMessage.class.getName()))
                .marshallingOutboundAllowedTypes(List.of(WormholeMessage.class.getName()))
                .build());
        this.doneFuture = new CompletableFuture<>();
        this.printStream = printStream;
        this.password = Crypto.randomString(16);
        this.sent = new AtomicBoolean();
    }

    @Override
    public void onEvent(final Event event) {
        if (event instanceof NodeUnrecoverableErrorEvent) {
            doneFuture.completeExceptionally(((NodeUnrecoverableErrorEvent) event).getError());
        }
        else if (event instanceof NodeNormalTerminationEvent) {
            doneFuture.complete(null);
        }
        else if (event instanceof MessageEvent) {
            final CompressedPublicKey receiver = ((MessageEvent) event).getSender();
            final Object message = ((MessageEvent) event).getPayload();

            if (message instanceof PasswordMessage) {
                if (text != null && password.equals(((PasswordMessage) message).getPassword()) && sent.compareAndSet(false, true)) {
                    sendText(receiver);
                }
                else {
                    wrongPassword(receiver);
                }
            }
        }
    }

    private void sendText(final CompressedPublicKey receiver) {
        send(receiver, new TextMessage(text)).whenComplete((result, e) -> {
            if (e != null) {
                doneFuture.completeExceptionally(e);
            }
            else {
                printStream.println("text message sent");
                doneFuture.complete(null);
            }
        });
    }

    private void wrongPassword(final CompressedPublicKey receiver) {
        send(receiver, new WrongPasswordMessage()).whenComplete((result, e) -> {
            if (e != null) {
                doneFuture.completeExceptionally(e);
            }
            else {
                doneFuture.completeExceptionally(new Exception(
                        "Code confirmation failed. Either you or your correspondent\n" +
                                "typed the code wrong, or a would-be man-in-the-middle attacker guessed\n" +
                                "incorrectly. You could try again, giving both your correspondent and\n" +
                                "the attacker another chance."
                ));
            }
        });
    }

    public CompletableFuture<Void> doneFuture() {
        return doneFuture;
    }

    public void setText(final String text) {
        this.text = text;
        final String code = identity().getPublicKey().toString() + password;
        printStream.println("Wormhole code is: " + code);
        printStream.println("On the other computer, please run:");
        printStream.println();
        printStream.println("drasyl wormhole receive " + code);
        printStream.println();
    }
}
