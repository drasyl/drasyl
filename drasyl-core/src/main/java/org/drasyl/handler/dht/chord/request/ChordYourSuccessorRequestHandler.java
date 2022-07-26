package org.drasyl.handler.dht.chord.request;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.Promise;
import org.drasyl.handler.dht.chord.message.ChordMessage;
import org.drasyl.handler.dht.chord.message.MySuccessor;
import org.drasyl.handler.dht.chord.message.NothingSuccessor;
import org.drasyl.handler.dht.chord.message.YourSuccessor;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

public class ChordYourSuccessorRequestHandler extends AbstractChordOneShotRequestHandler<ChordMessage, IdentityPublicKey> {
    private static final Logger LOG = LoggerFactory.getLogger(ChordYourSuccessorRequestHandler.class);

    public ChordYourSuccessorRequestHandler(final IdentityPublicKey peer,
                                            final Promise<IdentityPublicKey> promise) {
        super(YourSuccessor.of(), peer, promise);
    }

    @Override
    protected boolean acceptResponse(final Object msg) {
        return msg instanceof MySuccessor || msg instanceof NothingSuccessor;
    }

    @Override
    protected void handleResponse(final ChannelHandlerContext ctx,
                                  final ChordMessage response,
                                  final Promise<IdentityPublicKey> promise) {
        if (response instanceof MySuccessor) {
            promise.trySuccess(((MySuccessor) response).getAddress());
        }
        else {
            promise.trySuccess(null);
        }
    }

    @Override
    protected Logger logger() {
        return LOG;
    }
}
