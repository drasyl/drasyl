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
package org.drasyl.messenger;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.PeerChannelGroup;
import org.drasyl.peer.connection.message.RelayableMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.failedFuture;
import static org.drasyl.util.FutureUtil.toFuture;

/**
 * The Messenger is responsible for handling the outgoing message flow and sending messages to the
 * recipient.
 */
public class Messenger {
    private static final Logger LOG = LoggerFactory.getLogger(Messenger.class);
    private final Subject<CompressedPublicKey> communicationOccurred;
    private final MessageSink loopbackSink;
    private final MessageSink channelGroupSink;
    private MessageSink intraVmSink;

    public Messenger(final MessageSink loopbackSink,
                     final PeersManager peersManager,
                     final PeerChannelGroup channelGroup) {
        this(PublishSubject.create(), loopbackSink, null, message -> {
            final CompressedPublicKey recipient = message.getRecipient();

            try {
                return toFuture(channelGroup.writeAndFlush(recipient, message));
            }
            catch (final IllegalArgumentException e) {
                final CompressedPublicKey superPeer = peersManager.getSuperPeerKey();
                if (superPeer != null && !superPeer.equals(recipient)) {
                    // no direct connection, send message to super peer
                    try {
                        return toFuture(channelGroup.writeAndFlush(superPeer, message));
                    }
                    catch (final IllegalArgumentException e2) {
                        return failedFuture(new NoPathToPublicKeyException(recipient));
                    }
                }
                else {
                    return failedFuture(new NoPathToPublicKeyException(recipient));
                }
            }
        });
    }

    Messenger(final Subject<CompressedPublicKey> communicationOccurred,
              final MessageSink loopbackSink,
              final MessageSink intraVmSink,
              final MessageSink channelGroupSink) {
        this.communicationOccurred = requireNonNull(communicationOccurred);
        this.loopbackSink = requireNonNull(loopbackSink);
        this.intraVmSink = intraVmSink;
        this.channelGroupSink = channelGroupSink;
    }

    /**
     * Sends <code>message</code> to the recipient defined in the message. Throws a {@link
     * MessengerException} if sending is not possible (e.g. because no path to the peer exists).
     * <p>
     * The method tries to choose the best path to send the message to the recipient. Thus, it first
     * checks whether the recipient can be reached locally. Then it searches for direct connections
     * to the recipient. If there is no direct connection, the message is relayed to the Super
     * Peer.
     *
     * @param message message to be sent
     * @return a completed future if the message was successfully processed, otherwise an
     * exceptionally future
     */
    public CompletableFuture<Void> send(final RelayableMessage message) {
        LOG.trace("Send Message: {}", message);

        communicationOccurred.onNext(message.getRecipient());

        final LinkedList<MessageSink> messageSinks = new LinkedList<>();
        messageSinks.add(loopbackSink);
        messageSinks.add(intraVmSink);
        messageSinks.add(channelGroupSink);

        final CompletableFuture<Void> result = new CompletableFuture<>();
        sendWithMessageSinks(message, messageSinks, result);
        return result;
    }

    private void sendWithMessageSinks(final RelayableMessage message,
                                      final LinkedList<MessageSink> messageSinks,
                                      final CompletableFuture<Void> result) {
        try {
            // get next non-null MessageSink
            MessageSink messageSink;
            do {
                messageSink = messageSinks.removeFirst();
            }
            while (messageSink == null);

            // try to send message with current MessageSink
            messageSink.send(message).whenComplete((done, e) -> {
                if (e == null) {
                    // successfully sent
                    result.complete(null);
                }
                else {
                    // failure -> send with remaining MessageSinks
                    sendWithMessageSinks(message, messageSinks, result);
                }
            });
        }
        catch (final NoSuchElementException e) {
            // no MessageSinks remaining
            result.completeExceptionally(new NoPathToPublicKeyException(message.getRecipient()));
        }
    }

    public void setIntraVmSink(final MessageSink intraVmSink) {
        this.intraVmSink = intraVmSink;
    }

    public void unsetIntraVmSink() {
        this.intraVmSink = null;
    }

    /**
     * Returns an {@link Observable}, which emits the {@link CompressedPublicKey} of each peer a
     * message is sent to.
     *
     * @return an {@link Observable}, which emits the {@link CompressedPublicKey} of each peer a
     * message is sent to
     */
    public Observable<CompressedPublicKey> communicationOccurred() {
        return communicationOccurred;
    }
}