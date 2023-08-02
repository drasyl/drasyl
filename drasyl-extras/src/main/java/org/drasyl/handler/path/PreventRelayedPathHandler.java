package org.drasyl.handler.path;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.StringUtil;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.channel.OverlayAddressedMessage;
import org.drasyl.handler.remote.protocol.ApplicationMessage;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import static java.util.Objects.requireNonNull;

public class PreventRelayedPathHandler extends ChannelDuplexHandler {
    private static final Logger LOG = LoggerFactory.getLogger(PreventRelayedPathHandler.class);
    private final IdentityPublicKey peer;

    public PreventRelayedPathHandler(final IdentityPublicKey peer) {
        this.peer = requireNonNull(peer);
    }

    @Override
    public void write(final ChannelHandlerContext ctx,
                      final Object msg,
                      final ChannelPromise promise) throws Exception {
        if (msg instanceof OverlayAddressedMessage &&
                ((OverlayAddressedMessage<?>) msg).content() instanceof ApplicationMessage &&
                peer.equals(((ApplicationMessage) ((OverlayAddressedMessage<?>) msg).content()).getRecipient()) &&
                !(((DrasylServerChannel) ctx.channel()).isDirectPathPresent(((ApplicationMessage) ((OverlayAddressedMessage<?>) msg).content()).getRecipient()))
        ) {
            LOG.error("message `{}` could not be sent. no direct path to present. {} prevents any relayed communication.", msg, StringUtil.simpleClassName(this));
            promise.setFailure(new Exception("message could not be sent. no direct path to present. " + StringUtil.simpleClassName(this) + " prevents any relayed communication."));
            ReferenceCountUtil.release(msg);
        }
        else {
            ctx.write(msg, promise);
        }
    }
}
