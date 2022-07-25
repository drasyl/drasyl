package org.drasyl.handler.dht.chord;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.concurrent.Promise;
import org.drasyl.channel.OverlayAddressedMessage;
import org.drasyl.handler.dht.chord.message.MySuccessor;
import org.drasyl.identity.IdentityPublicKey;

public class ChordYourSuccessorRequestHandler extends SimpleChannelInboundHandler<OverlayAddressedMessage<MySuccessor>> {
    public ChordYourSuccessorRequestHandler(final IdentityPublicKey pre,
                                            final Promise<IdentityPublicKey> promise2) {

    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx,
                                final OverlayAddressedMessage<MySuccessor> msg) throws Exception {

    }
}
