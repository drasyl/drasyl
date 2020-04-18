package org.drasyl.core.client.transport.direct;

import org.drasyl.core.client.P2PActorRef;
import org.drasyl.core.client.transport.OutboundMessageEnvelope;
import org.drasyl.core.client.transport.P2PTransportChannel;
import org.drasyl.core.client.transport.P2PTransportChannelException;
import org.drasyl.core.client.transport.P2PTransportException;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Optional.ofNullable;

public abstract class AbstractDirectP2PTransportChannel implements P2PTransportChannel {
    private static final Logger log = LoggerFactory.getLogger(AbstractDirectP2PTransportChannel.class);
    private final Map<String, Channel> peers;
    private Random random;


    protected AbstractDirectP2PTransportChannel() {
        this.peers = new ConcurrentHashMap<>();
        random = new Random();
    }

    /**
     * Called by a channel handler when peer becomes available
     *
     * @param peerSystemName the peer which is now available
     */
    public synchronized void addPeer(String peerSystemName, Channel channel) {
        log.debug("New peer {}. Local channel address: {}", peerSystemName, channel.localAddress());
        Channel oldChannel = peers.put(peerSystemName, channel);
        if (oldChannel != channel) {
            shutdownChannel(oldChannel);
        }
    }

    /**
     * Called by a channel handler when peer is no longer available
     *
     * @param peerSystemName the peer which should be removed
     */
    public synchronized void removePeer(String peerSystemName) {
        Channel channel = peers.remove(peerSystemName);
        if (channel == null) {
            log.debug("Tried to remove nonexistent peer {}!", peerSystemName);
            return;
        }
        shutdownChannel(channel);
    }

    public synchronized void changePeerSystemName(String oldSystemName, String systemName) {
        Channel channelSlot = peers.get(systemName);
        if (channelSlot != null) {
            log.debug("Cannot change system name of {} to an already known peer: {}!", oldSystemName, systemName);
            return;
        }
        Channel channel = peers.remove(oldSystemName);
        if (channel == null) {
            log.debug("Cannot change system name of unknown peer: {} to {}!", oldSystemName, systemName);
            return;
        }
        peers.put(systemName, channel);
    }

    protected CompletableFuture<Void> shutdownAllPeers() {
        return CompletableFuture.allOf(peers.values().parallelStream()
                .map(this::shutdownChannel)
                .toArray(CompletableFuture<?>[]::new));
    }

    protected CompletableFuture<Void> shutdownChannel(Channel channel) {
        return CompletableFuture.runAsync(() -> channel
                .close()
                .syncUninterruptibly());
    }

    @Override
    public synchronized void send(OutboundMessageEnvelope outboundMessage) throws P2PTransportChannelException {
        if (peers.size() == 0) {
            throw new P2PTransportChannelException("No channel found for sending!");
        }

        String recipientSystem = outboundMessage.getRecipient().path().address().system();

        if (recipientSystem.equals("ANY")) {
            String[] peerSystemNames = peers.keySet().toArray(new String[0]);
            int randomPeer = random.nextInt(peerSystemNames.length);
            peers.get(peerSystemNames[randomPeer])
                    .writeAndFlush(outboundMessage);

        } else if (recipientSystem.equals("ALL")) {
            peers.values().forEach(c -> c.writeAndFlush(outboundMessage));
        } else {
            ofNullable(peers.get(recipientSystem))
                    .orElseThrow(() -> new P2PTransportException("No matching channel found for sending!"))
                    .writeAndFlush(outboundMessage);
        }

    }

    @Override
    public synchronized boolean accept(P2PActorRef recipient) {
        String recipientSystem = recipient.path().address().system();
        return peers.containsKey(recipientSystem);

    }

    public abstract void notifyError(String format, Throwable cause);
}
