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
package org.drasyl.peer.connection;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelId;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.peer.connection.message.QuitMessage;

import java.util.HashMap;
import java.util.Map;

import static java.util.Objects.requireNonNull;
import static org.drasyl.peer.connection.handler.ThreeWayHandshakeClientHandler.ATTRIBUTE_PUBLIC_KEY;
import static org.drasyl.peer.connection.message.QuitMessage.CloseReason.REASON_NEW_SESSION;

/**
 * Special type of {@link ChannelGroup}, which has a lookup complexity of O(1) instead of O(n) for
 * lookups by {@link CompressedPublicKey}.
 */
public class PeerChannelGroup extends DefaultChannelGroup {
    private final Map<CompressedPublicKey, ChannelId> identity2channelId;
    private final EventExecutor executor;
    private final ChannelFutureListener remover = future -> remove(future.channel());

    public PeerChannelGroup() {
        this(new HashMap<>(), GlobalEventExecutor.INSTANCE);
    }

    PeerChannelGroup(final Map<CompressedPublicKey, ChannelId> identity2channelId,
                     final EventExecutor executor) {
        super(executor);
        this.identity2channelId = identity2channelId;
        this.executor = executor;
    }

    public PeerChannelGroup(final EventExecutor executor) {
        this(new HashMap<>(), executor);
    }

    /**
     * @param publicKey the recipient of a message as compressed public key
     * @param message   the message to send
     * @return a completed future if the message was successfully processed, otherwise an
     * exceptionally future
     */
    public Future<Void> writeAndFlush(final CompressedPublicKey publicKey, final Object message) {
        final Channel existingChannel = find(publicKey);
        if (existingChannel != null) {
            return existingChannel.writeAndFlush(message);
        }
        else {
            return executor.newFailedFuture(new IllegalArgumentException("No channel with given Public Key found."));
        }
    }

    /**
     * Searches the channel for given public key.
     *
     * @param publicKey public key for which a channel should be searched
     * @return the {@code channel} if found, otherwise {@code null}
     */
    public Channel find(final CompressedPublicKey publicKey) {
        final ChannelId existingChannelId = identity2channelId.get(publicKey);
        if (existingChannelId != null) {
            return find(existingChannelId);
        }
        else {
            return null;
        }
    }

    @Override
    public boolean add(final Channel channel) {
        final CompressedPublicKey publicKey = channel.attr(ATTRIBUTE_PUBLIC_KEY).get();
        return add(publicKey, channel);
    }

    @Override
    public boolean remove(final Object o) {
        if (o instanceof Channel) {
            final Channel channel = (Channel) o;
            final CompressedPublicKey publicKey = channel.attr(ATTRIBUTE_PUBLIC_KEY).get();
            identity2channelId.remove(publicKey);
        }
        else if (o instanceof ChannelId) {
            identity2channelId.values().remove(o);
        }

        return super.remove(o);
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    @Override
    public boolean equals(final Object o) {
        return this == o;
    }

    public boolean add(final CompressedPublicKey identity, final Channel channel) {
        requireNonNull(identity);

        // close any existing connections with the same peer...
        final Channel existingChannel = find(identity);
        if (existingChannel != null) {
            existingChannel.writeAndFlush(new QuitMessage(REASON_NEW_SESSION)).addListener(ChannelFutureListener.CLOSE);
        }

        // ...before adding the new one
        channel.attr(ATTRIBUTE_PUBLIC_KEY).set(identity);
        final boolean added = super.add(channel);
        identity2channelId.put(identity, channel.id());

        if (added) {
            channel.closeFuture().addListener(remover);
        }

        return added;
    }
}