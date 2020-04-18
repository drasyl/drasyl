package city.sane.akka.p2p.transport;

import city.sane.akka.p2p.P2PActorRef;

import java.util.concurrent.CompletableFuture;

/**
 * Defines a channel that P2PTransport can use for communication between Actors.
 */
public interface P2PTransportChannel {
    /**
     * Starts the channel and makes it ready to receive messages. The future is completed when the channel is ready for
     * use.
     *
     * @return
     */
    CompletableFuture<Void> start();

    /**
     * Future which waits for channel shutdown
     *
     * @return completes exceptionally on error
     */
    CompletableFuture<Void> closeFuture();

    /**
     * Shuts down the channel. The future is completed when the channel is ready for use. The future is completed when
     * the channel is shut down
     *
     * @return
     */
    CompletableFuture<Void> shutdown();

    /**
     * Sends <code>message</code> via the channel to <code>recipient</code>.
     *  @param outboundMessage
     *
     */
    void send(OutboundMessageEnvelope outboundMessage) throws P2PTransportChannelException;

    /**
     * Receives <code>message</code> from the channel.
     *  @param inboundMessage
     *
     */
    void receive(InboundMessageEnvelope inboundMessage);

    /**
     * Returns <code>true</code> if this channel is able to send the message to <code>recipient</code>.
     *
     * @param recipient
     * @return
     */
    boolean accept(P2PActorRef recipient);
}
