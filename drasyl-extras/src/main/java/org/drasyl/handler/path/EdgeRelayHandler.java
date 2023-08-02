package org.drasyl.handler.path;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.channel.OverlayAddressedMessage;
import org.drasyl.handler.remote.protocol.ApplicationMessage;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import static java.util.Objects.requireNonNull;
import static org.drasyl.handler.noop.NoopDiscardHandler.NOOP_MAGIC_NUMBER;

public class EdgeRelayHandler extends ChannelDuplexHandler {
    private static final Logger LOG = LoggerFactory.getLogger(EdgeRelayHandler.class);
    private final int networkId;
    private final IdentityPublicKey myPublicKey;
    private final ProofOfWork myProofOfWork;
    private final IdentityPublicKey peer;
    private final IdentityPublicKey relay;

    public EdgeRelayHandler(final int networkId,
                            final IdentityPublicKey myPublicKey,
                            final ProofOfWork myProofOfWork,
                            final IdentityPublicKey peer,
                            final IdentityPublicKey relay) {
        this.networkId = networkId;
        this.myPublicKey = requireNonNull(myPublicKey);
        this.myProofOfWork = requireNonNull(myProofOfWork);
        this.peer = requireNonNull(peer);
        this.relay = requireNonNull(relay);
    }

    //@Override
    //public void channelActive(final ChannelHandlerContext ctx) {
    //    super.channelActive(ctx);
    //}

    //@Override
    //public void handlerAdded(final ChannelHandlerContext ctx) {
    //    super.handlerAdded(ctx);
    //}

    @SuppressWarnings("unchecked")
    @Override
    public void write(final ChannelHandlerContext ctx,
                      final Object msg,
                      final ChannelPromise promise) {
        if (isApplicationMessageToPeer(msg)) {
            final OverlayAddressedMessage<ApplicationMessage> originalMsg = (OverlayAddressedMessage<ApplicationMessage>) msg;

            if (((DrasylServerChannel) ctx.channel()).isDirectPathPresent(relay)) {
                // relay established
                final OverlayAddressedMessage<ApplicationMessage> relayedMsg = new OverlayAddressedMessage<>(originalMsg.content(), relay, originalMsg.sender());
                LOG.error("Relay message `{}` for {} through {}.", originalMsg.content().getNonce(), originalMsg.recipient(), relay);
                ctx.write(relayedMsg, promise);
            }
            else {
                // relay not present (yet)
                final ByteBuf byteBuf = ctx.alloc().buffer(Long.BYTES).writeLong(NOOP_MAGIC_NUMBER);
                final OverlayAddressedMessage<ApplicationMessage> msg2 = new OverlayAddressedMessage<>(ApplicationMessage.of(networkId, relay, myPublicKey, myProofOfWork, byteBuf), relay, (DrasylAddress) ctx.channel().localAddress());
                ctx.writeAndFlush(msg2).addListener((ChannelFutureListener) channelFuture -> {
                    if (channelFuture.cause() != null) {
                        LOG.warn("Error sending NOOP: ", channelFuture.cause());
                    }
                });

                ReferenceCountUtil.release(msg);
                promise.setFailure(new Exception("EdgeRelayHandler enforces all messages for " + originalMsg.recipient() + " to be relayed through " + relay + ". But no connection to relay is present (yet)."));
            }
        }
    }

    private boolean isApplicationMessageToPeer(Object msg) {
        return msg instanceof OverlayAddressedMessage &&
                (((OverlayAddressedMessage<?>) msg).content()) instanceof ApplicationMessage &&
                peer.equals(((ApplicationMessage) ((OverlayAddressedMessage<?>) msg).content()).getRecipient());
    }
}
